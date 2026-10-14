package com.platform.orderprocessing.messaging.producer;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Kafka producer responsible for dispatching serialized order events
 * using the order ID as partition key for sequential ordering.
 */
@Component
public class OrderEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String ordersTopic;

    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                              @Value("${platform.kafka.topics.orders:order.events}") String ordersTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.ordersTopic = ordersTopic;
    }

    /**
     * Sends an event payload to the orders topic asynchronously.
     *
     * @param partitionKey partition key (typically orderId) ensuring sequential ordering
     * @param payload serialized event JSON
     * @return CompletableFuture representing broker acknowledgment
     */
    public CompletableFuture<SendResult<String, String>> sendEvent(String partitionKey, String payload) {
        LOG.debug("Publishing event to topic '{}' with key '{}'", ordersTopic, partitionKey);
        return kafkaTemplate.send(ordersTopic, partitionKey, payload);
    }
}
