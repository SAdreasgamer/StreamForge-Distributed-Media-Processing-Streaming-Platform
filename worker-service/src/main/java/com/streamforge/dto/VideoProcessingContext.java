package com.streamforge.dto;

import com.streamforge.dto.event.ProcessingStatusEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class VideoProcessingContext {
    private final UUID videoId;
    private final String originalFilename;
    private final String storagePath;

    private Double durationSeconds;
    private Integer width;
    private Integer height;
    private Double fps;
    private String codec;
    private Integer bitrateKbps;
    private String audioCodec;

    private String thumbnailPath;
    private final List<ProcessingStatusEvent.VariantInfo> variants = new ArrayList<>();
}
