package com.streamforge.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionResponse(UUID sessionId, UUID videoId, String status, Instant expiresAt, Instant createdAt) {}
