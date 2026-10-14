package com.platform.orderprocessing.outbox.service;

import com.platform.orderprocessing.messaging.producer.OrderEventProducer;
import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import com.platform.orderprocessing.outbox.repository.OutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that polls pending outbox records and relays them to Kafka.
 * Guarantees at-least-once message delivery without distributed two-phase commits.
 */
@Service
public class OutboxPublisherService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventProducer orderEventProducer;
    private final int batchSize;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  OrderEventProducer orderEventProducer,
                                  @Value("${platform.outbox.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventProducer = orderEventProducer;
        this.batchSize = batchSize;
    }

    /**
     * Finds pending outbox events and dispatches them to Kafka.
     */
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(PageRequest.of(0, batchSize));
        if (pendingEvents.isEmpty()) {
            return;
        }

        LOG.debug("Found {} pending outbox events to dispatch", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                orderEventProducer.sendEvent(event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                markPublished(event.getId());
                                LOG.info("Successfully published outbox event {} ({}) to Kafka",
                                        event.getId(), event.getEventType());
                            } else {
                                markFailed(event.getId(), ex.getMessage());
                                LOG.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
                            }
                        });
            } catch (Exception ex) {
                markFailed(event.getId(), ex.getMessage());
                LOG.error("Immediate exception dispatching outbox event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(java.util.UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.markPublished();
            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(java.util.UUID eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.markFailed(errorMessage);
            outboxEventRepository.save(event);
        });
    }
}
