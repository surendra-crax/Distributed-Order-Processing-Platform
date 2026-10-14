package com.platform.orderprocessing.order.domain;

import java.util.Set;

/**
 * Valid states and state transition graph for an Order.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * Returns true if transition from this state to the next state is permitted.
     */
    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == FAILED;
            case CONFIRMED -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false; // Terminal states
        };
    }

    /**
     * Returns set of states that this status can legally transition to.
     */
    public Set<OrderStatus> allowableTransitions() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, FAILED);
            case CONFIRMED -> Set.of(PROCESSING, CANCELLED);
            case PROCESSING -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED, FAILED, CANCELLED -> Set.of();
        };
    }
}
