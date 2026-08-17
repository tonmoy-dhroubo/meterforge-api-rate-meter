package io.meterforge.worker.usageingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.UsageRecordedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UsageIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageIngestionConsumer.class);

    private final UsageIngestionService usageIngestionService;
    private final ObjectMapper objectMapper;

    public UsageIngestionConsumer(UsageIngestionService usageIngestionService, ObjectMapper objectMapper) {
        this.usageIngestionService = usageIngestionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${meterforge.kafka.topics.usage:meterforge.usage.v1}",
            groupId = "${meterforge.kafka.consumer.usage-group-id:meterforge-worker-usage-ingestion}"
    )
    public void consumeUsageEvent(String message) {
        try {
            UsageRecordedV1 event = objectMapper.readValue(message, UsageRecordedV1.class);
            usageIngestionService.ingestEvent(event);
        } catch (Exception e) {
            log.error("Failed to ingest usage event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
