package com.platform.orderprocessing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.service.InventoryService;
import com.platform.orderprocessing.messaging.domain.ProcessedEventId;
import com.platform.orderprocessing.messaging.producer.OrderEventProducer;
import com.platform.orderprocessing.messaging.repository.ProcessedEventRepository;
import com.platform.orderprocessing.order.domain.OrderStatus;
import com.platform.orderprocessing.order.dto.CreateOrderRequest;
import com.platform.orderprocessing.order.dto.OrderItemRequest;
import com.platform.orderprocessing.order.dto.OrderResponse;
import com.platform.orderprocessing.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"${platform.kafka.topics.orders}", "${platform.kafka.topics.orders-dlt}"})
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "platform.kafka.consumer.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
class KafkaConsumerIntegrationTest {

    @Autowired
    private OrderEventProducer orderEventProducer;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${platform.kafka.topics.orders}")
    private String ordersTopic;

    @Value("${platform.kafka.topics.orders-dlt}")
    private String ordersDltTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    private static final String CONSUMER_SKU = "TECH-CONSUMER-01";

    @BeforeEach
    void setUp() {
        try {
            inventoryService.createItem(new CreateInventoryItemRequest(
                    "PROD-CONS-01",
                    CONSUMER_SKU,
                    "Consumer Test Item",
                    50,
                    new BigDecimal("30.00")
            ));
        } catch (Exception ignored) {
            // Already created
        }
    }

    @Test
    @DisplayName("Should idempotently process OrderCreated event and transition order to CONFIRMED")
    void testIdempotentConsumerProcessesOrderCreated() {
        // 1. Create a real order in PENDING status
        CreateOrderRequest request = new CreateOrderRequest(
                "CUST-CONS-1",
                "cons@test.com",
                List.of(new OrderItemRequest(CONSUMER_SKU, 1)),
                "USD"
        );
        OrderResponse order = orderService.createOrder(request);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);

        UUID eventId = UUID.randomUUID();
        String eventPayload = String.format("""
                {"eventId":"%s","eventType":"OrderCreated","orderId":"%s","status":"PENDING"}
                """, eventId, order.id());

        // 2. Publish event to Kafka
        orderEventProducer.sendEvent(order.id().toString(), eventPayload);

        // 3. Await consumer processing: order status transitions to CONFIRMED
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            OrderResponse updatedOrder = orderService.getOrderById(order.id());
            assertThat(updatedOrder.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(processedEventRepository.existsById(new ProcessedEventId(eventId, consumerGroup))).isTrue();
        });

        // 4. Duplicate Delivery: Publish exact same event a second time
        orderEventProducer.sendEvent(order.id().toString(), eventPayload);

        // 5. Order remains CONFIRMED, deduplication entry still exists, no double transition error
        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            OrderResponse updatedOrder = orderService.getOrderById(order.id());
            assertThat(updatedOrder.status()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("Should route poison pill message to Dead Letter Topic (DLT) after retries")
    void testPoisonPillRoutingToDeadLetterTopic() {
        UUID poisonEventId = UUID.randomUUID();
        String poisonPill = String.format("""
                {"eventId":"%s","eventType":"OrderCreated","orderId":"%s","action":"TRIGGER_SIMULATED_FAILURE"}
                """, poisonEventId, UUID.randomUUID());

        // Create dedicated DLT reader
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-verifier-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> dltConsumer = cf.createConsumer()) {
            dltConsumer.subscribe(Collections.singletonList(ordersDltTopic));

            // Publish poison pill to main topic
            orderEventProducer.sendEvent("poison-key", poisonPill);

            // Await delivery to the Dead Letter Topic
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                assertThat(records).isNotEmpty();
                String receivedDltPayload = records.iterator().next().value();
                assertThat(receivedDltPayload).contains(poisonEventId.toString());
            });
        }
    }
}
