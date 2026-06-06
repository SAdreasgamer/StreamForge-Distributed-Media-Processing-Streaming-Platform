package com.streamforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create an upload session.
 * Kept in common since both upload-service (controller) and potentially other services may need it.
 */
public record CreateUploadSessionRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must be at most 500 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotBlank(message = "Content type is required")
        String contentType
) {}
