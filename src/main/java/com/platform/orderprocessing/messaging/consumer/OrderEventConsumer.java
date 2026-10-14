package com.platform.orderprocessing.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.messaging.domain.ProcessedEvent;
import com.platform.orderprocessing.messaging.domain.ProcessedEventId;
import com.platform.orderprocessing.messaging.repository.ProcessedEventRepository;
import com.platform.orderprocessing.order.domain.OrderStatus;
import com.platform.orderprocessing.order.service.OrderService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous Kafka consumer processing order lifecycle events.
 * Guarantees exactly-once business effects through deduplication persistence.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "platform.kafka.consumer.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public OrderEventConsumer(ProcessedEventRepository processedEventRepository,
                              OrderService orderService,
                              ObjectMapper objectMapper,
                              @Value("${spring.kafka.consumer.group-id:order-processing-group}") String consumerGroup) {
        this.processedEventRepository = processedEventRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    /**
     * Consumes order events from Kafka.
     * Enforces deduplication using the processed_events table before applying business changes.
     */
    @KafkaListener(topics = "${platform.kafka.topics.orders:order.events}",
                   groupId = "${spring.kafka.consumer.group-id:order-processing-group}",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consumeOrderEvent(ConsumerRecord<String, String> record) {
        String payload = record.value();
        LOG.info("Received event on partition {} offset {}: key={}", record.partition(), record.offset(), record.key());

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventIdStr = root.path("eventId").asText();
            String eventType = root.path("eventType").asText();
            String orderIdStr = root.path("orderId").asText();

            if (eventIdStr.isBlank()) {
                LOG.warn("Received malformed event missing eventId: {}", payload);
                return;
            }

            UUID eventId = UUID.fromString(eventIdStr);
            ProcessedEventId processedId = new ProcessedEventId(eventId, consumerGroup);

            // 1. Exactly-once deduplication check
            if (processedEventRepository.existsById(processedId)) {
                LOG.info("Duplicate event '{}' already processed for consumer group '{}'. Skipping to preserve exactly-once semantics.",
                        eventId, consumerGroup);
                return;
            }

            // 2. Simulated failure hook for testing Dead Letter Topic routing
            if (payload.contains("TRIGGER_SIMULATED_FAILURE")) {
                LOG.error("Encountered poison pill event {}. Triggering deliberate exception for DLT verification.", eventId);
                throw new IllegalStateException("Simulated consumer processing failure for event: " + eventId);
            }

            // 3. Mark event processed in database transaction
            processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup));

            // 4. Handle business state transition
            if ("OrderCreated".equalsIgnoreCase(eventType) && !orderIdStr.isBlank()) {
                UUID orderId = UUID.fromString(orderIdStr);
                orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
                LOG.info("Successfully transitioned order {} to CONFIRMED following event {}", orderId, eventId);
            }

        } catch (Exception ex) {
            LOG.error("Error processing Kafka event record: {}", ex.getMessage());
            throw new RuntimeException("Kafka consumer failed processing record", ex);
        }
    }
}
