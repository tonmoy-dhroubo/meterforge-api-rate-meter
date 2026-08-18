package io.meterforge.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.UsageDecision;
import io.meterforge.contracts.event.UsageOutcome;
import io.meterforge.contracts.event.UsageRecordedV1;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import io.meterforge.gateway.config.MeterForgeGatewayProperties;
import io.meterforge.gateway.credential.ApiKeyAuthenticator;
import io.meterforge.gateway.metering.UsageEventPublisher;
import io.meterforge.gateway.ratelimit.LuaRateLimiter;
import io.meterforge.gateway.ratelimit.RateLimitDecision;
import io.meterforge.gateway.routing.ProductRouteMatcher;
import io.meterforge.gateway.routing.SubscriptionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayProxyFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyFilter.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "x-api-key"
    );

    private final ApiKeyAuthenticator authenticator;
    private final ProductRouteMatcher routeMatcher;
    private final SubscriptionResolver subscriptionResolver;
    private final LuaRateLimiter rateLimiter;
    private final UsageEventPublisher usagePublisher;
    private final ProxyHttpClient proxyHttpClient;
    private final ObjectMapper objectMapper;
    private final MeterForgeGatewayProperties properties;

    public GatewayProxyFilter(
            ApiKeyAuthenticator authenticator,
            ProductRouteMatcher routeMatcher,
            SubscriptionResolver subscriptionResolver,
            LuaRateLimiter rateLimiter,
            UsageEventPublisher usagePublisher,
            ProxyHttpClient proxyHttpClient,
            ObjectMapper objectMapper,
            MeterForgeGatewayProperties properties) {
        this.authenticator = authenticator;
        this.routeMatcher = routeMatcher;
        this.subscriptionResolver = subscriptionResolver;
        this.rateLimiter = rateLimiter;
        this.usagePublisher = usagePublisher;
        this.proxyHttpClient = proxyHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        // Handle CORS preflight for browser tools like Request Lab
        if (request.getMethod() == HttpMethod.OPTIONS) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.OK);
            HttpHeaders headers = response.getHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "*");
            headers.set("Access-Control-Expose-Headers", "X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After, X-Request-ID, Content-Type");
            headers.set("Access-Control-Max-Age", "3600");
            return response.setComplete();
        }

        // Allow actuator health/metrics endpoints directly
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String requestId = resolveRequestId(request);
        long startNs = System.nanoTime();

        // 1. Authenticate API Key
        return authenticator.authenticate(request)
                .switchIfEmpty(Mono.defer(() -> {
                    emitUsage(requestId, null, null, null, null, null, null, null,
                            request.getMethod().name(), "<unauthorized>",
                            UsageDecision.UNAUTHORIZED, UsageOutcome.NOT_FORWARDED,
                            HttpStatus.UNAUTHORIZED.value(), 0, 0, null);
                    return sendProblemResponse(exchange, HttpStatus.UNAUTHORIZED,
                            "Unauthorized", "Invalid, expired, or missing API key", "INVALID_CREDENTIAL", requestId)
                            .then(Mono.empty());
                }))
                .flatMap(credential -> handleAuthenticatedRequest(exchange, request, credential, requestId, startNs));
    }

    private Mono<Void> handleAuthenticatedRequest(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            CredentialProjection credential,
            String requestId,
            long startNs) {

        String method = request.getMethod().name();

        // 2. Match Route
        return routeMatcher.match(request)
                .switchIfEmpty(Mono.defer(() -> {
                    emitUsage(requestId, credential.workspaceId(), null, null, credential.consumerId(),
                            credential.applicationId(), credential.credentialId(), null,
                            method, "<unmatched>",
                            UsageDecision.NOT_FOUND, UsageOutcome.NOT_FORWARDED,
                            HttpStatus.NOT_FOUND.value(), 0, 0, null);
                    return sendProblemResponse(exchange, HttpStatus.NOT_FOUND,
                            "Not Found", "No active route matches the requested path", "ROUTE_NOT_FOUND", requestId)
                            .then(Mono.empty());
                }))
                .flatMap(matchedRoute -> {
                    UUID productId = matchedRoute.product().productId();

                    // 3. Resolve Subscription
                    return subscriptionResolver.resolve(credential.applicationId(), productId)
                            .switchIfEmpty(Mono.defer(() -> {
                                emitUsage(requestId, credential.workspaceId(), productId, matchedRoute.route().routeId(),
                                        credential.consumerId(), credential.applicationId(), credential.credentialId(), null,
                                        method, matchedRoute.route().pathPattern(),
                                        UsageDecision.BLOCKED, UsageOutcome.NOT_FORWARDED,
                                        HttpStatus.FORBIDDEN.value(), 0, 0, null);
                                return sendProblemResponse(exchange, HttpStatus.FORBIDDEN,
                                        "Forbidden", "No active subscription found for this application and product",
                                        "NO_ACTIVE_SUBSCRIPTION", requestId)
                                        .then(Mono.empty());
                            }))
                            .flatMap(subscription -> handleLimitingAndProxy(
                                    exchange, request, credential, matchedRoute, subscription, requestId, startNs
                            ));
                });
    }

    private Mono<Void> handleLimitingAndProxy(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            CredentialProjection credential,
            ProductRouteMatcher.MatchedRoute matchedRoute,
            SubscriptionProjection subscription,
            String requestId,
            long startNs) {

        UUID routeId = matchedRoute.route().routeId();
        int costUnits = matchedRoute.costUnits();
        String method = request.getMethod().name();
        String routeTemplate = matchedRoute.route().pathPattern();

        // 4. Atomic Rate & Quota Limiter Evaluation
        return rateLimiter.evaluate(subscription, routeId, costUnits)
                .flatMap(decision -> {
                    if (!decision.allowed()) {
                        // Rate Limited (429)
                        emitUsage(requestId, credential.workspaceId(), matchedRoute.product().productId(),
                                routeId, credential.consumerId(), credential.applicationId(), credential.credentialId(),
                                subscription.subscriptionId(), method, routeTemplate,
                                UsageDecision.RATE_LIMITED, UsageOutcome.NOT_FORWARDED,
                                HttpStatus.TOO_MANY_REQUESTS.value(), 0, 0, decision.limitingPolicyId());

                        ServerHttpResponse response = exchange.getResponse();
                        response.getHeaders().add("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                        response.getHeaders().add("X-RateLimit-Remaining", "0");
                        response.getHeaders().add("X-RateLimit-Reset", String.valueOf(decision.resetAfterSeconds()));
                        response.getHeaders().add("X-Request-ID", requestId);

                        return sendProblemResponse(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                "Too Many Requests", "Rate limit or quota allowance exceeded. Please retry later.",
                                "RATE_LIMITED", requestId);
                    }

                    // 5. Allowed -> Proxy Upstream
                    return proxyUpstream(exchange, request, credential, matchedRoute, subscription, decision, requestId, startNs);
                })
                .onErrorResume(e -> {
                    log.error("Limiter failure / Redis unavailable for request {}: {}", requestId, e.getMessage(), e);
                    emitUsage(requestId, credential.workspaceId(), matchedRoute.product().productId(),
                            routeId, credential.consumerId(), credential.applicationId(), credential.credentialId(),
                            subscription.subscriptionId(), method, routeTemplate,
                            UsageDecision.BLOCKED, UsageOutcome.UNAVAILABLE,
                            HttpStatus.SERVICE_UNAVAILABLE.value(), 0, 0, null);

                    return sendProblemResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "Service Unavailable", "Rate limiter service is temporarily unavailable",
                            "LIMITER_UNAVAILABLE", requestId);
                });
    }

    private Mono<Void> proxyUpstream(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            CredentialProjection credential,
            ProductRouteMatcher.MatchedRoute matchedRoute,
            SubscriptionProjection subscription,
            RateLimitDecision decision,
            String requestId,
            long startNs) {

        String targetUrl = matchedRoute.targetUpstreamUrl();
        String query = request.getURI().getRawQuery();
        if (query != null && !query.isBlank()) {
            targetUrl += "?" + query;
        }

        HttpMethod httpMethod = request.getMethod();
        var clientReq = proxyHttpClient.getWebClient()
                .method(httpMethod)
                .uri(URI.create(targetUrl))
                .headers(headers -> copySafeHeaders(request.getHeaders(), headers, requestId));

        WebClient.RequestHeadersSpec<?> headersSpec = clientReq;
        if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH) {
            headersSpec = clientReq.body(BodyInserters.fromDataBuffers(request.getBody()));
        }

        return headersSpec.exchangeToMono(clientResponse -> {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            int statusCode = clientResponse.statusCode().value();
            UsageOutcome outcome = statusCode >= 500 ? UsageOutcome.SERVER_ERROR
                    : (statusCode >= 400 ? UsageOutcome.CLIENT_ERROR : UsageOutcome.SUCCESS);

            emitUsage(requestId, credential.workspaceId(), matchedRoute.product().productId(),
                    matchedRoute.route().routeId(), credential.consumerId(), credential.applicationId(),
                    credential.credentialId(), subscription.subscriptionId(),
                    request.getMethod().name(), matchedRoute.route().pathPattern(),
                    UsageDecision.ALLOWED, outcome, statusCode, matchedRoute.costUnits(),
                    latencyMs, null);

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(clientResponse.statusCode());
            copyResponseHeaders(clientResponse.headers().asHttpHeaders(), response.getHeaders());

            if (decision.remaining() >= 0) {
                response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
            }
            if (decision.resetAfterSeconds() > 0) {
                response.getHeaders().add("X-RateLimit-Reset", String.valueOf(decision.resetAfterSeconds()));
            }
            response.getHeaders().add("X-Request-ID", requestId);

            return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
        })
        .onErrorResume(e -> {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            HttpStatus errorStatus = HttpStatus.BAD_GATEWAY;
            UsageOutcome outcome = UsageOutcome.UNAVAILABLE;

            if (e instanceof java.util.concurrent.TimeoutException || e.getCause() instanceof io.netty.handler.timeout.ReadTimeoutException) {
                errorStatus = HttpStatus.GATEWAY_TIMEOUT;
                outcome = UsageOutcome.TIMEOUT;
            }

            emitUsage(requestId, credential.workspaceId(), matchedRoute.product().productId(),
                    matchedRoute.route().routeId(), credential.consumerId(), credential.applicationId(),
                    credential.credentialId(), subscription.subscriptionId(),
                    request.getMethod().name(), matchedRoute.route().pathPattern(),
                    UsageDecision.ALLOWED, outcome, errorStatus.value(),
                    matchedRoute.costUnits(), latencyMs, null);

            return sendProblemResponse(exchange, errorStatus,
                    errorStatus.getReasonPhrase(), "Upstream service unreachable or timed out",
                    "UPSTREAM_ERROR", requestId);
        });
    }

    private void copySafeHeaders(HttpHeaders incoming, HttpHeaders outgoing, String requestId) {
        incoming.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                outgoing.put(name, values);
            }
        });
        outgoing.set("X-Request-ID", requestId);
    }

    private void copyResponseHeaders(HttpHeaders incoming, HttpHeaders outgoing) {
        incoming.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                outgoing.put(name, values);
            }
        });
        outgoing.set("Access-Control-Allow-Origin", "*");
        outgoing.set("Access-Control-Expose-Headers", "X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After, X-Request-ID, Content-Type");
    }

    private Mono<Void> sendProblemResponse(
            ServerWebExchange exchange,
            HttpStatus status,
            String title,
            String detail,
            String code,
            String requestId) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("X-Request-ID", requestId);
        response.getHeaders().set("Access-Control-Allow-Origin", "*");
        response.getHeaders().set("Access-Control-Expose-Headers", "X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After, X-Request-ID, Content-Type");

        Map<String, Object> body = Map.of(
                "type", "https://meterforge.io/errors/" + code.toLowerCase().replace('_', '-'),
                "title", title,
                "status", status.value(),
                "detail", detail,
                "code", code,
                "requestId", requestId
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] fallback = ("{\"title\":\"" + title + "\",\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    private String resolveRequestId(ServerHttpRequest request) {
        String reqId = request.getHeaders().getFirst("X-Request-ID");
        if (reqId != null && !reqId.isBlank() && reqId.length() <= 64 && reqId.matches("^[a-zA-Z0-9_-]+$")) {
            return reqId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private void emitUsage(
            String requestId,
            UUID workspaceId,
            UUID productId,
            UUID routeId,
            UUID consumerId,
            UUID consumerApplicationId,
            UUID credentialId,
            UUID subscriptionId,
            String method,
            String routeTemplate,
            UsageDecision decision,
            UsageOutcome outcome,
            int statusCode,
            int usageUnits,
            long latencyMs,
            UUID limitingPolicyId) {

        UsageRecordedV1 event = UsageRecordedV1.create(
            requestId,
            workspaceId,
            productId,
            routeId,
            consumerId,
            consumerApplicationId,
            credentialId,
            subscriptionId,
            method,
            routeTemplate,
            decision,
            outcome,
            statusCode,
            usageUnits,
            latencyMs,
            limitingPolicyId,
            properties.getInstanceId()
        );

        usagePublisher.publish(event);
    }
}
