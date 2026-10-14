package com.platform.orderprocessing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for consumer deduplication records.
 */
@Embeddable
public class ProcessedEventId implements Serializable {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_group", nullable = false, length = 64)
    private String consumerGroup;

    public ProcessedEventId() {
    }

    public ProcessedEventId(UUID eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumerGroup, that.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerGroup);
    }
}
