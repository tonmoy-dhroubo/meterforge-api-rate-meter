package io.meterforge.gateway.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.ProductProjection;
import io.meterforge.contracts.projection.RouteProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Component
public class ProductRouteMatcher {

    private static final Logger log = LoggerFactory.getLogger(ProductRouteMatcher.class);
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ProductRouteMatcher(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void clearCache() {
        // No-op or clear if caching added
    }

    public record MatchedRoute(
            ProductProjection product,
            RouteProjection route,
            int costUnits,
            String targetUpstreamUrl
    ) {}

    public Mono<MatchedRoute> match(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().pathWithinApplication().value();

        return loadActiveProducts()
                .flatMap(products -> findMatchingRoute(products, method, path));
    }

    private Mono<List<ProductProjection>> loadActiveProducts() {
        return redisTemplate.keys("rf:v1:cfg:product:*")
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .mapNotNull(json -> {
                    try {
                        return objectMapper.readValue(json, ProductProjection.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(p -> p.status() == ResourceStatus.ACTIVE)
                .collectList();
    }

    private Mono<MatchedRoute> findMatchingRoute(List<ProductProjection> products, String method, String path) {
        for (ProductProjection product : products) {
            String basePath = product.gatewayBasePath();
            if (!path.startsWith(basePath)) {
                continue;
            }

            if (product.routes() == null || product.routes().isEmpty()) {
                continue;
            }

            // Find matching routes sorted by priority desc, specificity
            List<RouteProjection> matched = product.routes().stream()
                    .filter(r -> r.status() == ResourceStatus.ACTIVE)
                    .filter(r -> r.httpMethod().equalsIgnoreCase(method))
                    .filter(r -> matchesPath(r.pathPattern(), path))
                    .sorted(Comparator.comparingInt(RouteProjection::priority).reversed())
                    .toList();

            if (!matched.isEmpty()) {
                RouteProjection bestRoute = matched.get(0);
                String upstreamUrl = buildUpstreamUrl(product, bestRoute, path);
                return Mono.just(new MatchedRoute(
                        product,
                        bestRoute,
                        bestRoute.costUnits(),
                        upstreamUrl
                ));
            }
        }

        return Mono.empty();
    }

    private boolean matchesPath(String pattern, String path) {
        String antPattern = pattern.replaceAll("\\{[^}]+\\}", "*");
        return pathMatcher.match(pattern, path) || pathMatcher.match(antPattern, path);
    }

    private String buildUpstreamUrl(ProductProjection product, RouteProjection route, String requestPath) {
        String baseUrl = product.upstreamBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (route.upstreamPath() != null && !route.upstreamPath().isBlank()) {
            return baseUrl + route.upstreamPath();
        }

        return baseUrl + requestPath;
    }
}
