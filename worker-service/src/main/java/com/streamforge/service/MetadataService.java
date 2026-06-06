package com.streamforge.service;

import com.streamforge.config.FFmpegProperties;
import com.streamforge.dto.VideoProcessingContext;
import com.streamforge.exception.ProcessingException;
import com.streamforge.util.FFmpegUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final FFmpegProperties ffmpegProperties;

    public void extractMetadata(VideoProcessingContext context, Path videoFile) {
        log.info("Extracting metadata for video ID: {}", context.getVideoId());
        try {
            JsonNode probeResult = FFmpegUtil.probe(ffmpegProperties.getFfprobePath(), videoFile, Duration.ofSeconds(60));
            JsonNode format = probeResult.get("format");
            if (format != null) {
                if (format.has("duration")) {
                    context.setDurationSeconds(format.get("duration").asDouble());
                }
                if (format.has("bit_rate")) {
                    context.setBitrateKbps((int) (format.get("bit_rate").asLong() / 1000));
                }
            }
            JsonNode streams = probeResult.get("streams");
            if (streams != null && streams.isArray()) {
                for (JsonNode stream : streams) {
                    String codecType = stream.has("codec_type") ? stream.get("codec_type").asText() : "";
                    if ("video".equals(codecType)) {
                        if (stream.has("width")) context.setWidth(stream.get("width").asInt());
                        if (stream.has("height")) context.setHeight(stream.get("height").asInt());
                        if (stream.has("codec_name")) context.setCodec(stream.get("codec_name").asText());
                        if (stream.has("r_frame_rate")) context.setFps(parseFps(stream.get("r_frame_rate").asText()));
                    } else if ("audio".equals(codecType)) {
                        if (stream.has("codec_name")) context.setAudioCodec(stream.get("codec_name").asText());
                    }
                }
            }
            log.info("Metadata extracted for video {}: {}x{} @ {}fps", context.getVideoId(), context.getWidth(), context.getHeight(), context.getFps());
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingException("Metadata extraction failed for video: " + context.getVideoId(), e);
        }
    }

    private Double parseFps(String fpsStr) {
        try {
            if (fpsStr.contains("/")) {
                String[] parts = fpsStr.split("/");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                return den != 0 ? num / den : 0.0;
            }
            return Double.parseDouble(fpsStr);
        } catch (NumberFormatException e) {
            log.warn("Could not parse FPS: {}", fpsStr);
            return null;
        }
    }
}
