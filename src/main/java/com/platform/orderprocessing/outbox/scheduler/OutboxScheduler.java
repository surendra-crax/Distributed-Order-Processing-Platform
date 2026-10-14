package com.platform.orderprocessing.outbox.scheduler;

import com.platform.orderprocessing.outbox.service.OutboxPublisherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler polling the outbox table and triggering event dispatch.
 */
@Component
@ConditionalOnProperty(name = "platform.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;

    public OutboxScheduler(OutboxPublisherService outboxPublisherService) {
        this.outboxPublisherService = outboxPublisherService;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.polling-interval-ms:500}")
    public void pollAndPublish() {
        outboxPublisherService.publishPendingEvents();
    }
}
