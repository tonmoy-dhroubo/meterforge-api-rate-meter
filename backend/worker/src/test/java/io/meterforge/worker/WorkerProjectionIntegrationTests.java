package io.meterforge.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.CredentialConfigurationChangedV1;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.worker.outbox.OutboxPollerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class WorkerProjectionIntegrationTests {

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
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxPollerService outboxPollerService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS meterforge");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meterforge.outbox_events (
                id UUID PRIMARY KEY,
                event_id UUID NOT NULL UNIQUE,
                workspace_id UUID NOT NULL,
                aggregate_type VARCHAR(100) NOT NULL,
                aggregate_id UUID NOT NULL,
                aggregate_version BIGINT NOT NULL,
                event_type VARCHAR(100) NOT NULL,
                schema_version INT NOT NULL,
                payload JSONB NOT NULL,
                occurred_at TIMESTAMPTZ NOT NULL,
                published_at TIMESTAMPTZ,
                attempt_count INT NOT NULL DEFAULT 0,
                last_attempt_at TIMESTAMPTZ
            )
        """);
    }

    @Test
    @DisplayName("Outbox event is polled, published to Kafka, and projected to Redis with version guard")
    void testOutboxToRedisProjectionFlow() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID credId = UUID.randomUUID();
        String publicId = "pub_" + UUID.randomUUID().toString().substring(0, 8);

        CredentialConfigurationChangedV1 payload = new CredentialConfigurationChangedV1(
                credId,
                workspaceId,
                consumerId,
                appId,
                publicId,
                "hmac_hash_test_123456",
                "mf_dev_pub",
                "1234",
                "dev",
                ResourceStatus.ACTIVE,
                null,
                null,
                2L,
                Instant.now()
        );

        String payloadJson = objectMapper.writeValueAsString(payload);

        // 1. Insert outbox row
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO meterforge.outbox_events
            (id, event_id, workspace_id, aggregate_type, aggregate_id, aggregate_version, event_type, schema_version, payload, occurred_at)
            VALUES (?, ?, ?, 'ApiCredential', ?, 2, 'CredentialConfigurationChangedV1', 1, ?::jsonb, NOW())
        """, outboxId, eventId, workspaceId, credId, payloadJson);

        // 2. Poll and publish outbox
        outboxPollerService.pollAndPublish();

        // 3. Verify Redis projection appears
        String credKey = "rf:v1:cfg:credential:" + publicId;
        String versionKey = "rf:v1:cfg:version:ApiCredential:" + credId;

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    outboxPollerService.pollAndPublish();

                    String redisVal = redisTemplate.opsForValue().get(credKey);
                    assertThat(redisVal).isNotNull();

                    CredentialProjection projection = objectMapper.readValue(redisVal, CredentialProjection.class);
                    assertThat(projection.publicId()).isEqualTo(publicId);
                    assertThat(projection.secretHmac()).isEqualTo("hmac_hash_test_123456");
                    assertThat(projection.version()).isEqualTo(2L);

                    String savedVer = redisTemplate.opsForValue().get(versionKey);
                    assertThat(savedVer).isEqualTo("2");
                });

        // 4. Send an older version event (version 1) and verify Redis projection is NOT overwritten
        CredentialConfigurationChangedV1 stalePayload = new CredentialConfigurationChangedV1(
                credId,
                workspaceId,
                consumerId,
                appId,
                publicId,
                "STALE_HMAC",
                "mf_dev_pub",
                "1234",
                "dev",
                ResourceStatus.DISABLED,
                null,
                null,
                1L, // older version
                Instant.now()
        );

        ConfigEventEnvelope<Object> staleEnvelope = new ConfigEventEnvelope<>(
                1,
                UUID.randomUUID(),
                Instant.now(),
                workspaceId,
                "ApiCredential",
                credId,
                1L,
                "CredentialConfigurationChangedV1",
                stalePayload
        );

        String staleJson = objectMapper.writeValueAsString(staleEnvelope);
        kafkaTemplate.send("meterforge.config.v1", credId.toString(), staleJson).get(5, TimeUnit.SECONDS);

        // Wait a bit to ensure consumer processed it if it were going to
        Thread.sleep(1000);

        // Verify Redis STILL has version 2 and the original HMAC
        String redisValAfterStale = redisTemplate.opsForValue().get(credKey);
        CredentialProjection projAfter = objectMapper.readValue(redisValAfterStale, CredentialProjection.class);
        assertThat(projAfter.version()).isEqualTo(2L);
        assertThat(projAfter.secretHmac()).isEqualTo("hmac_hash_test_123456");
    }
}
