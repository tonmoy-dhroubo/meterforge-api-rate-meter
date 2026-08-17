package io.meterforge.gateway.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.UsageRecordedV1;
import io.meterforge.gateway.config.MeterForgeGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UsageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterForgeGatewayProperties properties;

    public UsageEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterForgeGatewayProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(UsageRecordedV1 event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String partitionKey = event.subscriptionId() != null
                    ? event.subscriptionId().toString()
                    : (event.credentialId() != null ? event.credentialId().toString() : event.requestId());

            CompletableFuture<?> future = kafkaTemplate.send(properties.getUsageTopic(), partitionKey, json);
            future.exceptionally(ex -> {
                log.warn("Failed to publish usage event {}: {}", event.eventId(), ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to serialize usage event {}: {}", event.eventId(), e.getMessage());
        }
    }
}
