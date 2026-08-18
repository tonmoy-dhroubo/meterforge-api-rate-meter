package io.meterforge.worker.usageingestion;

import io.meterforge.contracts.event.UsageRecordedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class UsageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(UsageIngestionService.class);

    private final JdbcTemplate jdbcTemplate;

    public UsageIngestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean ingestEvent(UsageRecordedV1 event) {
        String insertEventSql = """
            INSERT INTO meterforge.usage_events (
                event_id, occurred_at, received_at,
                workspace_id, product_id, route_id, consumer_id,
                application_id, credential_id, subscription_id,
                request_id, http_method, route_template,
                decision, outcome, status_code, usage_units,
                latency_ms, limiting_policy_id, gateway_instance_id
            ) VALUES (
                ?, ?, NOW(),
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?
            ) ON CONFLICT (event_id) DO NOTHING
        """;

        Timestamp occurredTimestamp = Timestamp.from(event.occurredAt() != null ? event.occurredAt() : Instant.now());

        int rowsInserted = jdbcTemplate.update(insertEventSql,
                event.eventId(),
                occurredTimestamp,
                event.workspaceId(),
                event.productId(),
                event.routeId(),
                event.consumerId(),
                event.consumerApplicationId(),
                event.credentialId(),
                event.subscriptionId(),
                truncate(event.requestId(), 64),
                truncate(event.method(), 16),
                truncate(event.routeTemplate(), 255),
                truncate(event.decision() != null ? event.decision().name() : "ALLOWED", 32),
                truncate(event.outcome() != null ? event.outcome().name() : "SUCCESS", 32),
                event.statusCode(),
                event.usageUnits(),
                event.latencyMs(),
                event.limitingPolicyId(),
                truncate(event.gatewayInstanceId(), 64)
        );

        if (rowsInserted == 0) {
            log.debug("Skipping duplicate usage event {} (idempotent no-op)", event.eventId());
            return false;
        }

        // Event was newly inserted -> atomically update rollups in same transaction
        Instant occurredInstant = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        Instant hourlyBucket = occurredInstant.truncatedTo(ChronoUnit.HOURS);
        Instant dailyBucket = occurredInstant.truncatedTo(ChronoUnit.DAYS);
        String statusClass = resolveStatusClass(event.statusCode());
        String decisionName = event.decision() != null ? event.decision().name() : "ALLOWED";

        upsertHourlyAggregate(event, hourlyBucket, statusClass, decisionName);
        upsertDailyAggregate(event, dailyBucket, statusClass, decisionName);

        log.debug("Ingested usage event {} for workspace {} (decision={}, status={})",
                event.eventId(), event.workspaceId(), decisionName, event.statusCode());
        return true;
    }

    private void upsertHourlyAggregate(UsageRecordedV1 event, Instant hourlyBucket, String statusClass, String decisionName) {
        String upsertHourlySql = """
            INSERT INTO meterforge.usage_hourly (
                bucket_start, workspace_id, product_id, route_id,
                consumer_id, application_id, subscription_id,
                decision, status_class, total_requests, total_units, total_latency_ms
            ) VALUES (
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, 1, ?, ?
            ) ON CONFLICT (
                bucket_start, workspace_id, product_id, route_id,
                consumer_id, application_id, subscription_id,
                decision, status_class
            ) DO UPDATE SET
                total_requests = meterforge.usage_hourly.total_requests + 1,
                total_units = meterforge.usage_hourly.total_units + EXCLUDED.total_units,
                total_latency_ms = meterforge.usage_hourly.total_latency_ms + EXCLUDED.total_latency_ms
        """;

        jdbcTemplate.update(upsertHourlySql,
                Timestamp.from(hourlyBucket),
                event.workspaceId(),
                event.productId(),
                event.routeId(),
                event.consumerId(),
                event.consumerApplicationId(),
                event.subscriptionId(),
                decisionName,
                statusClass,
                event.usageUnits(),
                event.latencyMs()
        );
    }

    private void upsertDailyAggregate(UsageRecordedV1 event, Instant dailyBucket, String statusClass, String decisionName) {
        String upsertDailySql = """
            INSERT INTO meterforge.usage_daily (
                bucket_start, workspace_id, product_id, route_id,
                consumer_id, application_id, subscription_id,
                decision, status_class, total_requests, total_units, total_latency_ms
            ) VALUES (
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, 1, ?, ?
            ) ON CONFLICT (
                bucket_start, workspace_id, product_id, route_id,
                consumer_id, application_id, subscription_id,
                decision, status_class
            ) DO UPDATE SET
                total_requests = meterforge.usage_daily.total_requests + 1,
                total_units = meterforge.usage_daily.total_units + EXCLUDED.total_units,
                total_latency_ms = meterforge.usage_daily.total_latency_ms + EXCLUDED.total_latency_ms
        """;

        jdbcTemplate.update(upsertDailySql,
                Timestamp.from(dailyBucket),
                event.workspaceId(),
                event.productId(),
                event.routeId(),
                event.consumerId(),
                event.consumerApplicationId(),
                event.subscriptionId(),
                decisionName,
                statusClass,
                event.usageUnits(),
                event.latencyMs()
        );
    }

    private String resolveStatusClass(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "2xx";
        if (statusCode >= 300 && statusCode < 400) return "3xx";
        if (statusCode >= 400 && statusCode < 500) return "4xx";
        if (statusCode >= 500 && statusCode < 600) return "5xx";
        return "ERR";
    }

    private String truncate(String val, int maxLength) {
        if (val == null) return null;
        if (val.length() <= maxLength) return val;
        return val.substring(0, maxLength);
    }
}
