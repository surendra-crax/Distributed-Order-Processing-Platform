package com.platform.orderprocessing.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standard RFC 7807-compliant problem details error response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        String correlationId,
        Map<String, String> validationErrors
) {
    public static ErrorResponse of(String type, String title, int status, String detail,
                                  String instance, String correlationId) {
        return new ErrorResponse(type, title, status, detail, instance, Instant.now(), correlationId, null);
    }

    public static ErrorResponse withValidation(String type, String title, int status, String detail,
                                              String instance, String correlationId,
                                              Map<String, String> validationErrors) {
        return new ErrorResponse(type, title, status, detail, instance, Instant.now(), correlationId, validationErrors);
    }
}
