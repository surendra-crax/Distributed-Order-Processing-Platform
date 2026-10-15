package com.platform.orderprocessing.messaging.producer;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Event producer responsible for dispatching serialized order events
 * using the order ID as partition key for sequential ordering.
 * Supports Kafka broker transport in production and local async dispatch in local dev mode.
 */
@Component
public class OrderEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LocalEventDispatcher localEventDispatcher;
    private final String ordersTopic;

    public OrderEventProducer(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            @Autowired(required = false) LocalEventDispatcher localEventDispatcher,
            @Value("${platform.kafka.topics.orders:order.events}") String ordersTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.localEventDispatcher = localEventDispatcher;
        this.ordersTopic = ordersTopic;
    }

    /**
     * Sends an event payload asynchronously.
     *
     * @param partitionKey partition key (typically orderId) ensuring sequential ordering
     * @param payload serialized event JSON
     * @return CompletableFuture representing broker or local dispatch acknowledgment
     */
    public CompletableFuture<?> sendEvent(String partitionKey, String payload) {
        if (kafkaTemplate != null) {
            LOG.debug("Publishing event to Kafka topic '{}' with key '{}'", ordersTopic, partitionKey);
            return kafkaTemplate.send(ordersTopic, partitionKey, payload);
        } else if (localEventDispatcher != null) {
            LOG.debug("Relaying event via LocalEventDispatcher for key '{}'", partitionKey);
            return localEventDispatcher.dispatch(partitionKey, payload);
        } else {
            LOG.warn("No messaging broker or local dispatcher available for key '{}'. Event discarded.", partitionKey);
            return CompletableFuture.completedFuture(null);
        }
    }
}

