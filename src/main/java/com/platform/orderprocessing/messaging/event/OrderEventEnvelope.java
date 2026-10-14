package com.platform.orderprocessing.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard envelope for distributed domain events published via Kafka.
 * Provides explicit schema and correlation metadata.
 *
 * @param <T> event payload type
 */
public record OrderEventEnvelope<T>(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String correlationId,
        int version,
        Instant timestamp,
        T payload
) {
    public static <T> OrderEventEnvelope<T> of(String eventType, String aggregateId,
                                               String correlationId, T payload) {
        return new OrderEventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                "ORDER",
                aggregateId,
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                1,
                Instant.now(),
                payload
        );
    }
}
