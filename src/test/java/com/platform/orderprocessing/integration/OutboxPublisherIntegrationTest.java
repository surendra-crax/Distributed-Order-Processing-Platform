package com.platform.orderprocessing.integration;

import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import com.platform.orderprocessing.outbox.repository.OutboxEventRepository;
import com.platform.orderprocessing.outbox.service.OutboxPublisherService;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"${platform.kafka.topics.orders}", "${platform.kafka.topics.orders-dlt}"})
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
class OutboxPublisherIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Test
    @DisplayName("Should poll pending outbox event, dispatch to Kafka, and mark status as PUBLISHED")
    void testOutboxEventPublishingFlow() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        String payload = String.format("""
                {"eventId":"%s","eventType":"OrderCreated","orderId":"%s","status":"PENDING"}
                """, eventId, orderId);

        OutboxEvent outboxEvent = new OutboxEvent(eventId, "ORDER", orderId.toString(), "OrderCreated", payload);
        outboxEventRepository.saveAndFlush(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);

        // Trigger outbox polling
        outboxPublisherService.publishPendingEvents();

        // Await asynchronous Kafka acknowledgment and status update to PUBLISHED
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEvent updated = outboxEventRepository.findById(eventId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OutboxEvent.STATUS_PUBLISHED);
            assertThat(updated.getPublishedAt()).isNotNull();
        });
    }
}
