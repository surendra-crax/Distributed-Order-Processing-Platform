package com.platform.orderprocessing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an Idempotency-Key is reused with a different request payload or in-flight collision.
 */
public class IdempotencyConflictException extends BusinessException {

    public IdempotencyConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "idempotency-key-conflict");
    }
}
