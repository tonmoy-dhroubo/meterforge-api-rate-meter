package io.meterforge.worker.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@EnableScheduling
public class OutboxPollerService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerService.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String configTopic;
    private final int batchSize;

    public OutboxPollerService(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${meterforge.kafka.topics.config:meterforge.config.v1}") String configTopic,
            @Value("${meterforge.outbox.batch-size:100}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.configTopic = configTopic;
        this.batchSize = batchSize;
    }

    public record OutboxRecord(
            UUID id,
            UUID eventId,
            UUID workspaceId,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            int schemaVersion,
            String payloadJson,
            Instant occurredAt
    ) {}

    @Scheduled(fixedDelayString = "${meterforge.outbox.poll-interval-ms:500}")
    public void pollAndPublish() {
        try {
            List<OutboxRecord> records = fetchUnpublishedRecords();
            if (records.isEmpty()) return;

            for (OutboxRecord record : records) {
                try {
                    Object payloadObj = objectMapper.readTree(record.payloadJson());
                    ConfigEventEnvelope<Object> envelope = new ConfigEventEnvelope<>(
                            record.schemaVersion(),
                            record.eventId(),
                            record.occurredAt(),
                            record.workspaceId(),
                            record.aggregateType(),
                            record.aggregateId(),
                            record.aggregateVersion(),
                            record.eventType(),
                            payloadObj
                    );

                    String envelopeJson = objectMapper.writeValueAsString(envelope);
                    kafkaTemplate.send(configTopic, record.aggregateId().toString(), envelopeJson)
                            .get(5, TimeUnit.SECONDS);

                    markPublished(record.id());
                    log.debug("Published outbox event {} for aggregate {} (version {})",
                            record.eventId(), record.aggregateId(), record.aggregateVersion());
                } catch (Exception e) {
                    log.error("Failed to publish outbox event {} of type {}: {}",
                            record.eventId(), record.eventType(), e.getMessage());
                    incrementAttempt(record.id());
                }
            }
        } catch (Exception e) {
            log.error("Error during outbox polling loop: {}", e.getMessage());
        }
    }

    private List<OutboxRecord> fetchUnpublishedRecords() {
        String sql = "SELECT id, event_id, workspace_id, aggregate_type, aggregate_id, aggregate_version, event_type, schema_version, payload, occurred_at " +
                "FROM meterforge.outbox_events WHERE published_at IS NULL ORDER BY occurred_at ASC LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getLong("aggregate_version"),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("payload"),
                rs.getTimestamp("occurred_at").toInstant()
        ), batchSize);
    }

    private void markPublished(UUID id) {
        jdbcTemplate.update("UPDATE meterforge.outbox_events SET published_at = NOW() WHERE id = ?", id);
    }

    private void incrementAttempt(UUID id) {
        jdbcTemplate.update("UPDATE meterforge.outbox_events SET attempt_count = attempt_count + 1, last_attempt_at = NOW() WHERE id = ?", id);
    }
}
