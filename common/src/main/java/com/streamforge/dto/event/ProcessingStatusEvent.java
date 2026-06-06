package com.streamforge.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event emitted by worker-service to report processing progress.
 * Consumed by catalog-service (to update video state) and notification-service (to push WebSocket updates).
 */
public record ProcessingStatusEvent(
    UUID videoId,
    String status,
    String message,
    VideoMetadata metadata,
    List<VariantInfo> variants,
    String thumbnailPath,
    String errorMessage,
    Instant timestamp
) {

    public record VideoMetadata(
        Double durationSeconds,
        Integer width,
        Integer height,
        Double fps,
        String codec,
        Integer bitrateKbps,
        String audioCodec
    ) {}

    public record VariantInfo(
        String resolution,
        Integer width,
        Integer height,
        Integer bitrateKbps,
        String manifestPath,
        String storagePath,
        Long fileSizeBytes
    ) {}
}
