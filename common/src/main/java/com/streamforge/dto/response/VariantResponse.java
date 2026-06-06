package com.streamforge.dto.response;

public record VariantResponse(String resolution, Integer width, Integer height,
                               Integer bitrateKbps, String playbackUrl) {}
