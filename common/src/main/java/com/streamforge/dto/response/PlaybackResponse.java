package com.streamforge.dto.response;

import java.util.List;
import java.util.UUID;

public record PlaybackResponse(UUID videoId, String title, String masterManifestUrl,
                                List<VariantResponse> variants, String thumbnailUrl) {}
