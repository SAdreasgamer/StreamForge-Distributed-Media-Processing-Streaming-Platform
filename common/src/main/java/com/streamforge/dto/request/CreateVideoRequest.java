package com.streamforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new video record in the catalog service.
 * Used by upload-service via the Feign client.
 */
public record CreateVideoRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must be at most 500 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotBlank(message = "Original filename is required")
        String originalFilename,

        @NotBlank(message = "Content type is required")
        String contentType
) {}
