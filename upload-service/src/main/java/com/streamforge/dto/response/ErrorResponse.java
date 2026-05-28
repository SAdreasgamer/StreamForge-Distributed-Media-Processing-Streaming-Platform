package com.streamforge.dto.response;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
