package com.streamforge.service;

import com.streamforge.config.FFmpegProperties;
import com.streamforge.exception.ProcessingException;
import com.streamforge.model.Video;
import com.streamforge.repository.VideoRepository;
import com.streamforge.util.FFmpegUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final FFmpegProperties ffmpegProperties;
    private final VideoRepository videoRepository;

    @Transactional
    public void extractMetadata(Video video, Path videoFile) {
        log.info("Extracting metadata for video: {}", video.getId());
        try {
            JsonNode probeResult = FFmpegUtil.probe(ffmpegProperties.getFfprobePath(), videoFile, Duration.ofSeconds(60));
            JsonNode format = probeResult.get("format");
            if (format != null) {
                if (format.has("duration")) video.setDurationSeconds(format.get("duration").asDouble());
                if (format.has("bit_rate")) video.setBitrateKbps((int) (format.get("bit_rate").asLong() / 1000));
            }
            JsonNode streams = probeResult.get("streams");
            if (streams != null && streams.isArray()) {
                for (JsonNode stream : streams) {
                    String codecType = stream.has("codec_type") ? stream.get("codec_type").asText() : "";
                    if ("video".equals(codecType)) {
                        if (stream.has("width")) video.setWidth(stream.get("width").asInt());
                        if (stream.has("height")) video.setHeight(stream.get("height").asInt());
                        if (stream.has("codec_name")) video.setCodec(stream.get("codec_name").asText());
                        if (stream.has("r_frame_rate")) video.setFps(parseFps(stream.get("r_frame_rate").asText()));
                    } else if ("audio".equals(codecType)) {
                        if (stream.has("codec_name")) video.setAudioCodec(stream.get("codec_name").asText());
                    }
                }
            }
            videoRepository.save(video);
            log.info("Metadata extracted for video {}: {}x{} @ {}fps", video.getId(), video.getWidth(), video.getHeight(), video.getFps());
        } catch (ProcessingException e) { throw e;
        } catch (Exception e) { throw new ProcessingException("Metadata extraction failed for video: " + video.getId(), e); }
    }

    private Double parseFps(String fpsStr) {
        try {
            if (fpsStr.contains("/")) {
                String[] parts = fpsStr.split("/");
                double num = Double.parseDouble(parts[0]), den = Double.parseDouble(parts[1]);
                return den != 0 ? num / den : 0.0;
            }
            return Double.parseDouble(fpsStr);
        } catch (NumberFormatException e) { log.warn("Could not parse FPS: {}", fpsStr); return null; }
    }
}
