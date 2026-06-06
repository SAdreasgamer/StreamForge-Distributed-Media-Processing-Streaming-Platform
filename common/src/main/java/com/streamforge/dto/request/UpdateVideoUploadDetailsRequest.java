package com.streamforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateVideoUploadDetailsRequest(
    @NotBlank(message = "Original filename is required")
    String originalFilename,

    @NotBlank(message = "Storage path is required")
    String storagePath,

    @NotNull(message = "File size is required")
    Long fileSizeBytes
) {}
