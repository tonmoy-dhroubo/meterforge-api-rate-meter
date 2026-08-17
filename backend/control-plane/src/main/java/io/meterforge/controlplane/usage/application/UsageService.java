package io.meterforge.controlplane.usage.application;

import io.meterforge.controlplane.usage.api.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UsageService {

    private static final String TIMESERIES_HOURLY_SQL_PREFIX = """
        SELECT
            bucket_start,
            COALESCE(SUM(total_requests), 0) AS total_requests,
            COALESCE(SUM(CASE WHEN decision = 'ALLOWED' THEN total_requests ELSE 0 END), 0) AS allowed_requests,
            COALESCE(SUM(CASE WHEN decision = 'RATE_LIMITED' THEN total_requests ELSE 0 END), 0) AS rate_limited_requests,
            COALESCE(SUM(CASE WHEN status_class = '5xx' OR (status_class = '4xx' AND decision != 'RATE_LIMITED') THEN total_requests ELSE 0 END), 0) AS error_requests,
            COALESCE(SUM(total_units), 0) AS total_units,
            COALESCE(SUM(total_latency_ms), 0) AS sum_latency
        FROM meterforge.usage_hourly
        WHERE workspace_id = ?
          AND bucket_start >= ?
          AND bucket_start <= ?
    """;

    private static final String TIMESERIES_DAILY_SQL_PREFIX = """
        SELECT
            bucket_start,
            COALESCE(SUM(total_requests), 0) AS total_requests,
            COALESCE(SUM(CASE WHEN decision = 'ALLOWED' THEN total_requests ELSE 0 END), 0) AS allowed_requests,
            COALESCE(SUM(CASE WHEN decision = 'RATE_LIMITED' THEN total_requests ELSE 0 END), 0) AS rate_limited_requests,
            COALESCE(SUM(CASE WHEN status_class = '5xx' OR (status_class = '4xx' AND decision != 'RATE_LIMITED') THEN total_requests ELSE 0 END), 0) AS error_requests,
            COALESCE(SUM(total_units), 0) AS total_units,
            COALESCE(SUM(total_latency_ms), 0) AS sum_latency
        FROM meterforge.usage_daily
        WHERE workspace_id = ?
          AND bucket_start >= ?
          AND bucket_start <= ?
    """;

    private final JdbcTemplate jdbcTemplate;

    public UsageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UsageSummaryResponse getUsageSummary(
            UUID workspaceId,
            Instant from,
            Instant to,
            UUID productId,
            UUID consumerId) {

        TimeRange range = resolveTimeRange(from, to, Duration.ofDays(1));

        StringBuilder sql = new StringBuilder("""
            SELECT
                COALESCE(SUM(total_requests), 0) AS total_requests,
                COALESCE(SUM(CASE WHEN decision = 'ALLOWED' THEN total_requests ELSE 0 END), 0) AS allowed_requests,
                COALESCE(SUM(CASE WHEN decision = 'RATE_LIMITED' THEN total_requests ELSE 0 END), 0) AS rate_limited_requests,
                COALESCE(SUM(CASE WHEN decision IN ('BLOCKED', 'UNAUTHORIZED', 'NOT_FOUND') THEN total_requests ELSE 0 END), 0) AS blocked_requests,
                COALESCE(SUM(CASE WHEN status_class = '4xx' AND decision != 'RATE_LIMITED' THEN total_requests ELSE 0 END), 0) AS client_error_requests,
                COALESCE(SUM(CASE WHEN status_class = '5xx' THEN total_requests ELSE 0 END), 0) AS server_error_requests,
                COALESCE(SUM(total_units), 0) AS total_units,
                COALESCE(SUM(total_latency_ms), 0) AS sum_latency
            FROM meterforge.usage_hourly
            WHERE workspace_id = ?
              AND bucket_start >= ?
              AND bucket_start <= ?
        """);

        List<Object> params = new ArrayList<>();
        params.add(workspaceId);
        params.add(range.from());
        params.add(range.to());

        if (productId != null) {
            sql.append(" AND product_id = ?");
            params.add(productId);
        }
        if (consumerId != null) {
            sql.append(" AND consumer_id = ?");
            params.add(consumerId);
        }

        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
            long total = rs.getLong("total_requests");
            long sumLatency = rs.getLong("sum_latency");
            double avgLatency = total > 0 ? (double) sumLatency / total : 0.0;

            return new UsageSummaryResponse(
                    total,
                    rs.getLong("allowed_requests"),
                    rs.getLong("rate_limited_requests"),
                    rs.getLong("blocked_requests"),
                    rs.getLong("client_error_requests"),
                    rs.getLong("server_error_requests"),
                    rs.getLong("total_units"),
                    Math.round(avgLatency * 100.0) / 100.0
            );
        }, params.toArray());
    }

    public UsageTimeseriesResponse getUsageTimeseries(
            UUID workspaceId,
            Instant from,
            Instant to,
            String granularity,
            UUID productId,
            UUID consumerId) {

        TimeRange range = resolveTimeRange(from, to, Duration.ofDays(1));
        boolean isDaily = granularity != null && granularity.equalsIgnoreCase("DAY");
        String gran = isDaily ? "DAY" : "HOUR";

        StringBuilder sql = new StringBuilder(isDaily ? TIMESERIES_DAILY_SQL_PREFIX : TIMESERIES_HOURLY_SQL_PREFIX);

        List<Object> params = new ArrayList<>();
        params.add(workspaceId);
        params.add(range.from());
        params.add(range.to());

        if (productId != null) {
            sql.append(" AND product_id = ?");
            params.add(productId);
        }
        if (consumerId != null) {
            sql.append(" AND consumer_id = ?");
            params.add(consumerId);
        }

        sql.append(" GROUP BY bucket_start ORDER BY bucket_start ASC");

        List<TimeseriesBucketDto> buckets = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long total = rs.getLong("total_requests");
            long sumLatency = rs.getLong("sum_latency");
            double avgLatency = total > 0 ? (double) sumLatency / total : 0.0;

            return new TimeseriesBucketDto(
                    rs.getTimestamp("bucket_start").toInstant(),
                    total,
                    rs.getLong("allowed_requests"),
                    rs.getLong("rate_limited_requests"),
                    rs.getLong("error_requests"),
                    rs.getLong("total_units"),
                    Math.round(avgLatency * 100.0) / 100.0
            );
        }, params.toArray());

        return new UsageTimeseriesResponse(gran, range.fromInstant(), range.toInstant(), buckets);
    }

    public List<TopRouteDto> getTopRoutes(UUID workspaceId, Instant from, Instant to, int limit) {
        TimeRange range = resolveTimeRange(from, to, Duration.ofDays(7));
        int safeLimit = limit > 0 && limit <= 50 ? limit : 5;

        String sql = """
            SELECT
                r.id AS route_id,
                r.http_method,
                r.path_pattern,
                COALESCE(SUM(u.total_requests), 0) AS total_requests,
                COALESCE(SUM(u.total_units), 0) AS total_units
            FROM meterforge.usage_hourly u
            JOIN meterforge.api_routes r ON u.route_id = r.id
            WHERE u.workspace_id = ?
              AND u.bucket_start >= ?
              AND u.bucket_start <= ?
            GROUP BY r.id, r.http_method, r.path_pattern
            ORDER BY total_requests DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TopRouteDto(
                rs.getObject("route_id", UUID.class),
                rs.getString("http_method"),
                rs.getString("path_pattern"),
                rs.getLong("total_requests"),
                rs.getLong("total_units")
        ), workspaceId, range.from(), range.to(), safeLimit);
    }

    public List<TopApplicationDto> getTopApplications(UUID workspaceId, Instant from, Instant to, int limit) {
        TimeRange range = resolveTimeRange(from, to, Duration.ofDays(7));
        int safeLimit = limit > 0 && limit <= 50 ? limit : 5;

        String sql = """
            SELECT
                a.id AS application_id,
                a.name AS application_name,
                COALESCE(SUM(u.total_requests), 0) AS total_requests,
                COALESCE(SUM(u.total_units), 0) AS total_units
            FROM meterforge.usage_hourly u
            JOIN meterforge.consumer_applications a ON u.application_id = a.id
            WHERE u.workspace_id = ?
              AND u.bucket_start >= ?
              AND u.bucket_start <= ?
            GROUP BY a.id, a.name
            ORDER BY total_requests DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TopApplicationDto(
                rs.getObject("application_id", UUID.class),
                rs.getString("application_name"),
                rs.getLong("total_requests"),
                rs.getLong("total_units")
        ), workspaceId, range.from(), range.to(), safeLimit);
    }

    /**
     * Retrieves paginated raw usage event traces for operational inspection.
     * Note: Total count query and item query execute under a single read-only transaction.
     */
    public RawUsageEventsPageDto getRawUsageEvents(
            UUID workspaceId,
            Instant from,
            Instant to,
            UUID productId,
            UUID consumerId,
            String decision,
            int limit,
            int offset) {

        int safeLimit = limit > 0 && limit <= 100 ? limit : 50;
        int safeOffset = Math.max(0, offset);

        StringBuilder whereClause = new StringBuilder(" WHERE workspace_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(workspaceId);

        if (from != null) {
            whereClause.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            whereClause.append(" AND occurred_at <= ?");
            params.add(Timestamp.from(to));
        }
        if (productId != null) {
            whereClause.append(" AND product_id = ?");
            params.add(productId);
        }
        if (consumerId != null) {
            whereClause.append(" AND consumer_id = ?");
            params.add(consumerId);
        }
        if (decision != null && !decision.isBlank()) {
            whereClause.append(" AND decision = ?");
            params.add(decision.toUpperCase());
        }

        String countSql = "SELECT count(*) FROM meterforge.usage_events" + whereClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total != null ? total : 0L;

        String selectSql = """
            SELECT
                event_id, occurred_at, request_id, workspace_id,
                product_id, route_id, http_method, route_template,
                consumer_id, application_id, credential_id, subscription_id,
                decision, outcome, status_code, usage_units,
                latency_ms, limiting_policy_id, gateway_instance_id
            FROM meterforge.usage_events
        """ + whereClause + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?";

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeLimit);
        queryParams.add(safeOffset);

        List<RawUsageEventDto> items = jdbcTemplate.query(selectSql, (rs, rowNum) -> new RawUsageEventDto(
                rs.getObject("event_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("request_id"),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getObject("route_id", UUID.class),
                rs.getString("http_method"),
                rs.getString("route_template"),
                rs.getObject("consumer_id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getObject("credential_id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                rs.getString("decision"),
                rs.getString("outcome"),
                rs.getInt("status_code"),
                rs.getInt("usage_units"),
                rs.getLong("latency_ms"),
                rs.getObject("limiting_policy_id", UUID.class),
                rs.getString("gateway_instance_id")
        ), queryParams.toArray());

        return new RawUsageEventsPageDto(items, totalCount, safeLimit, safeOffset);
    }

    public Optional<RawUsageEventDto> getUsageEventById(UUID workspaceId, UUID eventId) {
        String sql = """
            SELECT
                event_id, occurred_at, request_id, workspace_id,
                product_id, route_id, http_method, route_template,
                consumer_id, application_id, credential_id, subscription_id,
                decision, outcome, status_code, usage_units,
                latency_ms, limiting_policy_id, gateway_instance_id
            FROM meterforge.usage_events
            WHERE workspace_id = ? AND event_id = ?
        """;

        List<RawUsageEventDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> new RawUsageEventDto(
                rs.getObject("event_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("request_id"),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getObject("route_id", UUID.class),
                rs.getString("http_method"),
                rs.getString("route_template"),
                rs.getObject("consumer_id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getObject("credential_id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                rs.getString("decision"),
                rs.getString("outcome"),
                rs.getInt("status_code"),
                rs.getInt("usage_units"),
                rs.getLong("latency_ms"),
                rs.getObject("limiting_policy_id", UUID.class),
                rs.getString("gateway_instance_id")
        ), workspaceId, eventId);

        return results.stream().findFirst();
    }

    private record TimeRange(Timestamp from, Timestamp to, Instant fromInstant, Instant toInstant) {}

    private TimeRange resolveTimeRange(Instant from, Instant to, Duration defaultLookback) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(defaultLookback);
        return new TimeRange(
                Timestamp.from(effectiveFrom),
                Timestamp.from(effectiveTo),
                effectiveFrom,
                effectiveTo
        );
    }
}
