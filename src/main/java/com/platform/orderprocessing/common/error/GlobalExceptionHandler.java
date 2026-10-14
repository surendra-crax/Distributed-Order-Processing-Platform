package com.platform.orderprocessing.common.error;

import com.platform.orderprocessing.common.correlation.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler rendering RFC 7807 Problem Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://platform.com/errors/";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        LOG.warn("Business rule violation: [{}] - {}", ex.getErrorType(), ex.getMessage());
        String correlationId = getCorrelationId();

        ErrorResponse response = ErrorResponse.of(
                ERROR_BASE_URI + ex.getErrorType(),
                ex.getStatus().getReasonPhrase(),
                ex.getStatus().value(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        LOG.warn("Request validation failed on URI: {}", request.getRequestURI());
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String correlationId = getCorrelationId();
        ErrorResponse response = ErrorResponse.withValidation(
                ERROR_BASE_URI + "validation-error",
                "Validation Failed",
                HttpStatus.BAD_REQUEST.value(),
                "One or more request parameters failed validation.",
                request.getRequestURI(),
                correlationId,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        LOG.warn("Malformed HTTP request body: {}", ex.getMessage());
        String correlationId = getCorrelationId();
        ErrorResponse response = ErrorResponse.of(
                ERROR_BASE_URI + "malformed-request-body",
                "Malformed Request Body",
                HttpStatus.BAD_REQUEST.value(),
                "Request body is missing or unparseable JSON.",
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled internal server error on URI {}: ", request.getRequestURI(), ex);
        String correlationId = getCorrelationId();
        ErrorResponse response = ErrorResponse.of(
                ERROR_BASE_URI + "internal-server-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please reference correlation ID: " + correlationId,
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getCorrelationId() {
        String corrId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return corrId != null ? corrId : "none";
    }
}
