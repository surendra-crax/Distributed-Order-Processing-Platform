package com.platform.orderprocessing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked business exception with associated HTTP status code and machine-readable error type.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorType;

    public BusinessException(String message, HttpStatus status, String errorType) {
        super(message);
        this.status = status;
        this.errorType = errorType;
    }

    public BusinessException(String message, Throwable cause, HttpStatus status, String errorType) {
        super(message, cause);
        this.status = status;
        this.errorType = errorType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }
}
