package com.streamforge.dto.response;

import java.time.Instant;
import java.util.UUID;

public record VideoResponse(UUID id, String title, String status, String thumbnailUrl,
                             Double durationSeconds, String resolution, Long fileSizeBytes, Instant createdAt) {}
