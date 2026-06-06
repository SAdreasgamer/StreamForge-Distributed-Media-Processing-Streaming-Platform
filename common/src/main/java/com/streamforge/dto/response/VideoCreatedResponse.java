package com.streamforge.dto.response;

import java.util.UUID;

/**
 * Response returned by catalog-service when a new video record is created.
 * Used by the Feign CatalogServiceClient.
 */
public record VideoCreatedResponse(UUID videoId) {}
