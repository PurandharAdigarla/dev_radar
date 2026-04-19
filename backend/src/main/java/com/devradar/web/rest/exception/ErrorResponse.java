package com.devradar.web.rest.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(int status, String message, Instant timestamp, Map<String, String> errors) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Instant.now(), null);
    }
    public static ErrorResponse of(int status, String message, Map<String, String> errors) {
        return new ErrorResponse(status, message, Instant.now(), errors);
    }
}
