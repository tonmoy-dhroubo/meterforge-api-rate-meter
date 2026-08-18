package io.meterforge.worker.configprojection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.contracts.projection.PlanProjection;
import io.meterforge.contracts.projection.PolicyProjection;
import io.meterforge.contracts.projection.ProductProjection;
import io.meterforge.contracts.projection.RouteProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigProjectionBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(ConfigProjectionBootstrapService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConfigProjectionBootstrapService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapConfigProjections() {
        log.info("Starting Redis configuration projection reconciliation from PostgreSQL...");
        try {
            bootstrapProductsAndRoutes();
            bootstrapPlansAndPolicies();
            bootstrapCredentials();
            bootstrapSubscriptions();
            log.info("Redis configuration projections successfully reconciled.");
        } catch (Exception e) {
            log.error("Failed to bootstrap Redis configuration projections: {}", e.getMessage(), e);
        }
    }

    private void bootstrapProductsAndRoutes() {
        String productsSql = "SELECT id, workspace_id, name, slug, upstream_base_url, gateway_base_path, status, version " +
                "FROM meterforge.api_products";

        String routesSql = "SELECT id, product_id, http_method, path_pattern, upstream_path, cost_units, priority, status, version " +
                "FROM meterforge.api_routes";

        Map<UUID, List<RouteProjection>> routesByProduct = new HashMap<>();
        jdbcTemplate.query(routesSql, rs -> {
            UUID routeId = (UUID) rs.getObject("id");
            UUID productId = (UUID) rs.getObject("product_id");
            String httpMethod = rs.getString("http_method");
            String pathPattern = rs.getString("path_pattern");
            String upstreamPath = rs.getString("upstream_path");
            int costUnits = rs.getInt("cost_units");
            int priority = rs.getInt("priority");
            ResourceStatus status = ResourceStatus.valueOf(rs.getString("status"));
            long version = rs.getLong("version");

            RouteProjection route = new RouteProjection(
                    routeId, httpMethod, pathPattern, upstreamPath, costUnits, priority, status, version
            );
            routesByProduct.computeIfAbsent(productId, k -> new ArrayList<>()).add(route);
        });

        jdbcTemplate.query(productsSql, rs -> {
            UUID productId = (UUID) rs.getObject("id");
            UUID workspaceId = (UUID) rs.getObject("workspace_id");
            String name = rs.getString("name");
            String slug = rs.getString("slug");
            String upstreamBaseUrl = rs.getString("upstream_base_url");
            String gatewayBasePath = rs.getString("gateway_base_path");
            ResourceStatus status = ResourceStatus.valueOf(rs.getString("status"));
            long version = rs.getLong("version");

            List<RouteProjection> routes = routesByProduct.getOrDefault(productId, List.of());
            ProductProjection projection = new ProductProjection(
                    productId, workspaceId, name, slug, upstreamBaseUrl, gatewayBasePath, status, routes, version
            );

            try {
                String prodKey = "rf:v1:cfg:product:" + productId;
                redisTemplate.opsForValue().set(prodKey, objectMapper.writeValueAsString(projection));

                if (status == ResourceStatus.ACTIVE) {
                    redisTemplate.opsForSet().add(ConfigProjectionConsumer.ACTIVE_PRODUCTS_SET_KEY, productId.toString());
                } else {
                    redisTemplate.opsForSet().remove(ConfigProjectionConsumer.ACTIVE_PRODUCTS_SET_KEY, productId.toString());
                }

                redisTemplate.opsForValue().set("rf:v1:cfg:version:PRODUCT:" + productId, String.valueOf(version));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:ApiProduct:" + productId, String.valueOf(version));
            } catch (Exception e) {
                log.error("Failed to project product {} to Redis: {}", productId, e.getMessage());
            }
        });
    }

    private void bootstrapPlansAndPolicies() {
        String plansSql = "SELECT id, workspace_id, product_id, name, slug, status, version " +
                "FROM meterforge.plans";

        String policiesSql = "SELECT id, plan_id, route_id, kind, capacity, refill_tokens, refill_period_seconds, " +
                "quota_limit, quota_period, enabled " +
                "FROM meterforge.limit_policies";

        Map<UUID, List<PolicyProjection>> policiesByPlan = new HashMap<>();
        jdbcTemplate.query(policiesSql, rs -> {
            UUID policyId = (UUID) rs.getObject("id");
            UUID planId = (UUID) rs.getObject("plan_id");
            UUID routeId = (UUID) rs.getObject("route_id");
            LimitPolicyKind kind = LimitPolicyKind.valueOf(rs.getString("kind"));
            Integer capacity = (Integer) rs.getObject("capacity");
            Integer refillTokens = (Integer) rs.getObject("refill_tokens");
            Integer refillPeriodSeconds = (Integer) rs.getObject("refill_period_seconds");
            Long quotaLimit = (Long) rs.getObject("quota_limit");
            String qp = rs.getString("quota_period");
            QuotaPeriod quotaPeriod = qp != null ? QuotaPeriod.valueOf(qp) : null;
            boolean enabled = rs.getBoolean("enabled");

            PolicyProjection pol = new PolicyProjection(
                    policyId, routeId, kind, capacity, refillTokens, refillPeriodSeconds, quotaLimit, quotaPeriod, enabled
            );
            policiesByPlan.computeIfAbsent(planId, k -> new ArrayList<>()).add(pol);
        });

        jdbcTemplate.query(plansSql, rs -> {
            UUID planId = (UUID) rs.getObject("id");
            UUID workspaceId = (UUID) rs.getObject("workspace_id");
            UUID productId = (UUID) rs.getObject("product_id");
            String name = rs.getString("name");
            String slug = rs.getString("slug");
            ResourceStatus status = ResourceStatus.valueOf(rs.getString("status"));
            long version = rs.getLong("version");

            List<PolicyProjection> policies = policiesByPlan.getOrDefault(planId, List.of());
            PlanProjection projection = new PlanProjection(
                    planId, workspaceId, productId, name, slug, status, policies, version
            );

            try {
                String planKey = "rf:v1:cfg:plan:" + planId;
                redisTemplate.opsForValue().set(planKey, objectMapper.writeValueAsString(projection));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:PLAN:" + planId, String.valueOf(version));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:Plan:" + planId, String.valueOf(version));
            } catch (Exception e) {
                log.error("Failed to project plan {} to Redis: {}", planId, e.getMessage());
            }
        });
    }

    private void bootstrapCredentials() {
        String sql = "SELECT c.id, c.workspace_id, a.consumer_id, c.application_id, c.public_id, c.secret_hmac, " +
                "c.environment, c.status, c.expires_at, c.revoked_at, c.version " +
                "FROM meterforge.api_credentials c " +
                "JOIN meterforge.consumer_applications a ON c.application_id = a.id";

        jdbcTemplate.query(sql, rs -> {
            UUID credId = (UUID) rs.getObject("id");
            UUID workspaceId = (UUID) rs.getObject("workspace_id");
            UUID consumerId = (UUID) rs.getObject("consumer_id");
            UUID applicationId = (UUID) rs.getObject("application_id");
            String publicId = rs.getString("public_id");
            String secretHmac = rs.getString("secret_hmac");
            String environment = rs.getString("environment");
            ResourceStatus status = ResourceStatus.valueOf(rs.getString("status"));
            Timestamp expTs = rs.getTimestamp("expires_at");
            Instant expiresAt = expTs != null ? expTs.toInstant() : null;
            Timestamp revTs = rs.getTimestamp("revoked_at");
            Instant revokedAt = revTs != null ? revTs.toInstant() : null;
            long version = rs.getLong("version");

            CredentialProjection projection = new CredentialProjection(
                    credId, workspaceId, consumerId, applicationId, publicId, secretHmac, environment, status, expiresAt, revokedAt, version
            );

            try {
                String credKey = "rf:v1:cfg:credential:" + publicId;
                redisTemplate.opsForValue().set(credKey, objectMapper.writeValueAsString(projection));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:CREDENTIAL:" + credId, String.valueOf(version));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:ApiCredential:" + credId, String.valueOf(version));
            } catch (Exception e) {
                log.error("Failed to project credential {} to Redis: {}", credId, e.getMessage());
            }
        });
    }

    private void bootstrapSubscriptions() {
        String sql = "SELECT s.id, s.workspace_id, a.consumer_id, s.application_id, s.product_id, s.plan_id, " +
                "s.status, s.effective_from, s.effective_to, s.version " +
                "FROM meterforge.subscriptions s " +
                "JOIN meterforge.consumer_applications a ON s.application_id = a.id";

        jdbcTemplate.query(sql, rs -> {
            UUID subscriptionId = (UUID) rs.getObject("id");
            UUID workspaceId = (UUID) rs.getObject("workspace_id");
            UUID consumerId = (UUID) rs.getObject("consumer_id");
            UUID applicationId = (UUID) rs.getObject("application_id");
            UUID productId = (UUID) rs.getObject("product_id");
            UUID planId = (UUID) rs.getObject("plan_id");
            ResourceStatus status = ResourceStatus.valueOf(rs.getString("status"));
            Timestamp fromTs = rs.getTimestamp("effective_from");
            Instant effectiveFrom = fromTs != null ? fromTs.toInstant() : Instant.now();
            Timestamp toTs = rs.getTimestamp("effective_to");
            Instant effectiveTo = toTs != null ? toTs.toInstant() : null;
            long version = rs.getLong("version");

            List<PolicyProjection> policies = new ArrayList<>();
            try {
                String planJson = redisTemplate.opsForValue().get("rf:v1:cfg:plan:" + planId);
                if (planJson != null) {
                    PlanProjection planProj = objectMapper.readValue(planJson, PlanProjection.class);
                    if (planProj.policies() != null) {
                        policies.addAll(planProj.policies());
                    }
                }
            } catch (Exception ignored) {}

            SubscriptionProjection projection = new SubscriptionProjection(
                    subscriptionId, workspaceId, consumerId, applicationId, productId, planId, status, policies, effectiveFrom, effectiveTo, version
            );

            try {
                String subKey = "rf:v1:cfg:subscription:" + subscriptionId;
                String appSubKey = "rf:v1:cfg:app-sub:" + applicationId + ":" + productId;
                redisTemplate.opsForValue().set(subKey, objectMapper.writeValueAsString(projection));
                redisTemplate.opsForValue().set(appSubKey, subscriptionId.toString());
                redisTemplate.opsForValue().set("rf:v1:cfg:version:SUBSCRIPTION:" + subscriptionId, String.valueOf(version));
                redisTemplate.opsForValue().set("rf:v1:cfg:version:Subscription:" + subscriptionId, String.valueOf(version));
            } catch (Exception e) {
                log.error("Failed to project subscription {} to Redis: {}", subscriptionId, e.getMessage());
            }
        });
    }
}
