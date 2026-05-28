package com.streamforge.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StatusResponse(UUID videoId, String status, String errorMessage, Instant updatedAt) {}
