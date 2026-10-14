package com.platform.orderprocessing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource (e.g. order, product) does not exist.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "resource-not-found");
    }
}
