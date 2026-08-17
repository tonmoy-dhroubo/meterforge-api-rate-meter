package io.meterforge.gateway.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.ProductProjection;
import io.meterforge.contracts.projection.RouteProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Component
public class ProductRouteMatcher {

    private static final Logger log = LoggerFactory.getLogger(ProductRouteMatcher.class);
    public static final String ACTIVE_PRODUCTS_SET_KEY = "rf:v1:cfg:products";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Small bounded L1 cache for active products list (5 seconds TTL)
    private final Cache<String, List<ProductProjection>> productsCache = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    public ProductRouteMatcher(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void clearCache() {
        productsCache.invalidateAll();
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
        List<ProductProjection> cached = productsCache.getIfPresent("active_products");
        if (cached != null) {
            return Mono.just(cached);
        }

        return redisTemplate.opsForSet().members(ACTIVE_PRODUCTS_SET_KEY)
                .flatMap(productIdStr -> redisTemplate.opsForValue().get("rf:v1:cfg:product:" + productIdStr))
                .switchIfEmpty(
                        // Fallback using non-blocking SCAN if active_products set is not yet populated
                        redisTemplate.scan(ScanOptions.scanOptions().match("rf:v1:cfg:product:*").count(100).build())
                                .flatMap(key -> redisTemplate.opsForValue().get(key))
                )
                .mapNotNull(json -> {
                    try {
                        return objectMapper.readValue(json, ProductProjection.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(p -> p.status() == ResourceStatus.ACTIVE)
                .collectList()
                .doOnNext(list -> productsCache.put("active_products", list));
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

            // Find matching routes sorted by specificity (static > variable > wildcard) then priority
            List<RouteProjection> matched = product.routes().stream()
                    .filter(r -> r.status() == ResourceStatus.ACTIVE)
                    .filter(r -> r.httpMethod().equalsIgnoreCase(method))
                    .filter(r -> matchesPath(r.pathPattern(), path))
                    .sorted(Comparator.comparingInt(this::calculateSpecificityScore).reversed()
                            .thenComparing(Comparator.comparingInt(RouteProjection::priority).reversed()))
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

    private int calculateSpecificityScore(RouteProjection route) {
        String pattern = route.pathPattern();
        int score = 0;
        if (!pattern.contains("{") && !pattern.contains("*")) {
            score += 1000; // purely static
        } else if (!pattern.contains("*")) {
            score += 500; // variable parameter
        } else {
            score += 100; // wildcard (** or *)
        }

        // Add bonus for static segment lengths
        String[] segments = pattern.split("/");
        for (String segment : segments) {
            if (!segment.isBlank() && !segment.startsWith("{") && !segment.contains("*")) {
                score += segment.length() * 2;
            }
        }
        return score;
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
