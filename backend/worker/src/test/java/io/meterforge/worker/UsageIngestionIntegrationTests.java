package io.meterforge.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.UsageDecision;
import io.meterforge.contracts.event.UsageOutcome;
import io.meterforge.contracts.event.UsageRecordedV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UsageIngestionIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.0-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS meterforge");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meterforge.usage_events (
                event_id UUID PRIMARY KEY,
                occurred_at TIMESTAMPTZ NOT NULL,
                received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                workspace_id UUID,
                product_id UUID,
                route_id UUID,
                consumer_id UUID,
                application_id UUID,
                credential_id UUID,
                subscription_id UUID,
                request_id VARCHAR(255) NOT NULL,
                http_method VARCHAR(20) NOT NULL,
                route_template VARCHAR(1024),
                decision VARCHAR(50) NOT NULL,
                outcome VARCHAR(50) NOT NULL,
                status_code INT NOT NULL,
                usage_units INT NOT NULL DEFAULT 0,
                latency_ms BIGINT NOT NULL DEFAULT 0,
                limiting_policy_id UUID,
                gateway_instance_id VARCHAR(100)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meterforge.usage_hourly (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                bucket_start TIMESTAMPTZ NOT NULL,
                workspace_id UUID,
                product_id UUID,
                route_id UUID,
                consumer_id UUID,
                application_id UUID,
                subscription_id UUID,
                decision VARCHAR(50) NOT NULL,
                status_class VARCHAR(10) NOT NULL,
                total_requests BIGINT NOT NULL DEFAULT 0,
                total_units BIGINT NOT NULL DEFAULT 0,
                total_latency_ms BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uq_usage_hourly UNIQUE NULLS NOT DISTINCT (
                    bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class
                )
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meterforge.usage_daily (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                bucket_start TIMESTAMPTZ NOT NULL,
                workspace_id UUID,
                product_id UUID,
                route_id UUID,
                consumer_id UUID,
                application_id UUID,
                subscription_id UUID,
                decision VARCHAR(50) NOT NULL,
                status_class VARCHAR(10) NOT NULL,
                total_requests BIGINT NOT NULL DEFAULT 0,
                total_units BIGINT NOT NULL DEFAULT 0,
                total_latency_ms BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uq_usage_daily UNIQUE NULLS NOT DISTINCT (
                    bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class
                )
            )
        """);

        jdbcTemplate.execute("DELETE FROM meterforge.usage_events");
        jdbcTemplate.execute("DELETE FROM meterforge.usage_hourly");
        jdbcTemplate.execute("DELETE FROM meterforge.usage_daily");
    }

    @Test
    @DisplayName("Valid usage event is consumed, persisted to raw events table, and aggregated into hourly and daily rollups")
    void testUsageEventIngestedAndAggregated() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID credId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        UsageRecordedV1 event = new UsageRecordedV1(
                1,
                eventId,
                Instant.now(),
                "req-12345",
                workspaceId,
                productId,
                routeId,
                consumerId,
                appId,
                credId,
                subId,
                "GET",
                "/v1/forecast/{city}",
                UsageDecision.ALLOWED,
                UsageOutcome.SUCCESS,
                200,
                1,
                25L,
                null,
                "gateway-test-1"
        );

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("meterforge.usage.v1", eventId.toString(), json).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Integer eventCount = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM meterforge.usage_events WHERE event_id = ?",
                            Integer.class, eventId);
                    assertThat(eventCount).isEqualTo(1);

                    Integer hourlyTotalRequests = jdbcTemplate.queryForObject(
                            "SELECT total_requests FROM meterforge.usage_hourly WHERE workspace_id = ? AND decision = 'ALLOWED'",
                            Integer.class, workspaceId);
                    assertThat(hourlyTotalRequests).isEqualTo(1);

                    Integer dailyTotalRequests = jdbcTemplate.queryForObject(
                            "SELECT total_requests FROM meterforge.usage_daily WHERE workspace_id = ? AND decision = 'ALLOWED'",
                            Integer.class, workspaceId);
                    assertThat(dailyTotalRequests).isEqualTo(1);

                    Long dailyUnits = jdbcTemplate.queryForObject(
                            "SELECT total_units FROM meterforge.usage_daily WHERE workspace_id = ?",
                            Long.class, workspaceId);
                    assertThat(dailyUnits).isEqualTo(1L);

                    Long dailyLatency = jdbcTemplate.queryForObject(
                            "SELECT total_latency_ms FROM meterforge.usage_daily WHERE workspace_id = ?",
                            Long.class, workspaceId);
                    assertThat(dailyLatency).isEqualTo(25L);
                });
    }

    @Test
    @DisplayName("Redelivered duplicate usage event is deduplicated and does not double-count in aggregates")
    void testDuplicateUsageEventDoesNotDoubleCount() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        UsageRecordedV1 event = new UsageRecordedV1(
                1,
                eventId,
                Instant.now(),
                "req-duplicate-test",
                workspaceId,
                productId,
                null,
                null,
                null,
                null,
                null,
                "GET",
                "/v1/test",
                UsageDecision.ALLOWED,
                UsageOutcome.SUCCESS,
                200,
                2,
                40L,
                null,
                "gateway-test-1"
        );

        String json = objectMapper.writeValueAsString(event);

        // 1. Send first time
        kafkaTemplate.send("meterforge.usage.v1", eventId.toString(), json).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Integer eventCount = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM meterforge.usage_events WHERE event_id = ?",
                            Integer.class, eventId);
                    assertThat(eventCount).isEqualTo(1);
                });

        // 2. Send same eventId again (duplicate redelivery)
        kafkaTemplate.send("meterforge.usage.v1", eventId.toString(), json).get(5, TimeUnit.SECONDS);

        // 3. Send a follow-up sentinel event to guarantee the duplicate message in the queue has been processed
        UUID sentinelEventId = UUID.randomUUID();
        UsageRecordedV1 sentinelEvent = new UsageRecordedV1(
                1,
                sentinelEventId,
                Instant.now(),
                "req-sentinel",
                workspaceId,
                productId,
                null,
                null,
                null,
                null,
                null,
                "GET",
                "/v1/sentinel",
                UsageDecision.ALLOWED,
                UsageOutcome.SUCCESS,
                200,
                1,
                10L,
                null,
                "gateway-test-1"
        );
        String sentinelJson = objectMapper.writeValueAsString(sentinelEvent);
        kafkaTemplate.send("meterforge.usage.v1", sentinelEventId.toString(), sentinelJson).get(5, TimeUnit.SECONDS);

        // Wait until the sentinel event is fully consumed and processed
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Integer sentinelCount = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM meterforge.usage_events WHERE event_id = ?",
                            Integer.class, sentinelEventId);
                    assertThat(sentinelCount).isEqualTo(1);
                });

        // 4. Verify exactly 1 raw event for original eventId and aggregates reflect single event counts (plus sentinel)
        Integer originalEventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM meterforge.usage_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(originalEventCount).isEqualTo(1);

        // Total requests for workspace = 1 original + 1 sentinel = 2 (NOT 3)
        Integer hourlyRequests = jdbcTemplate.queryForObject(
                "SELECT SUM(total_requests) FROM meterforge.usage_hourly WHERE workspace_id = ?",
                Integer.class, workspaceId);
        assertThat(hourlyRequests).isEqualTo(2);

        // Total units for workspace = 2 original + 1 sentinel = 3 (NOT 5)
        Long hourlyUnits = jdbcTemplate.queryForObject(
                "SELECT SUM(total_units) FROM meterforge.usage_hourly WHERE workspace_id = ?",
                Long.class, workspaceId);
        assertThat(hourlyUnits).isEqualTo(3L);
    }
}
