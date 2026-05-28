package com.streamforge.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VideoDetailResponse(UUID id, String title, String description, String status,
                                   String originalFilename, Long fileSizeBytes, Double durationSeconds,
                                   Integer width, Integer height, Double fps, String codec,
                                   Integer bitrateKbps, String audioCodec, String thumbnailUrl,
                                   String errorMessage, List<VariantResponse> variants,
                                   Instant createdAt, Instant updatedAt) {}
