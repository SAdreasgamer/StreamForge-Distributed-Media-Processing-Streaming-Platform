package com.streamforge.dto.event;

import java.util.UUID;

public record VideoUploadedEvent(
    UUID videoId,
    String title,
    String description,
    String originalFilename,
    String storagePath,
    long fileSizeBytes
) {}
