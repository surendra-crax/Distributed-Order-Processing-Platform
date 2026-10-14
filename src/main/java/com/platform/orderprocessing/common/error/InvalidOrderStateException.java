package com.platform.orderprocessing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an invalid order lifecycle transition is attempted.
 */
public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "invalid-order-state-transition");
    }
}
