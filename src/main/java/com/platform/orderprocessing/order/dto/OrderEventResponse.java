package com.platform.orderprocessing.order.dto;

import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing an order lifecycle event in the audit trail.
 */
public record OrderEventResponse(
        UUID eventId,
        String aggregateId,
        String eventType,
        String status,
        int retryCount,
        Instant createdAt,
        Instant publishedAt,
        String payload
) {
    public static OrderEventResponse from(OutboxEvent outboxEvent) {
        return new OrderEventResponse(
                outboxEvent.getId(),
                outboxEvent.getAggregateId(),
                outboxEvent.getEventType(),
                outboxEvent.getStatus(),
                outboxEvent.getRetryCount(),
                outboxEvent.getCreatedAt(),
                outboxEvent.getPublishedAt(),
                outboxEvent.getPayload()
        );
    }
}
