package com.streamforge.exception;

import java.util.UUID;

public class UploadSessionExpiredException extends RuntimeException {
    public UploadSessionExpiredException(UUID sessionId) {
        super("Upload session expired or not found: " + sessionId);
    }
}
