package com.streamforge.controller;

import com.streamforge.config.MinioConfig;
import com.streamforge.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.UUID;

@Tag(name = "Streaming", description = "Endpoints for proxying adaptive bitrate video streams (HLS)")
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StreamController {

    private final StorageService storageService;
    private final MinioConfig minioConfig;

    @Operation(summary = "Get HLS master manifest")
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<StreamingResponseBody> getMasterManifest(@PathVariable UUID videoId) {
        String objectPath = videoId.toString() + "/hls/master.m3u8";
        return streamObject(objectPath, "application/x-mpegURL");
    }

    @Operation(summary = "Get HLS variant playlist")
    @GetMapping("/{videoId}/{resolution}/playlist.m3u8")
    public ResponseEntity<StreamingResponseBody> getVariantPlaylist(
            @PathVariable UUID videoId,
            @PathVariable String resolution) {
        String objectPath = videoId.toString() + "/hls/" + resolution + "/playlist.m3u8";
        return streamObject(objectPath, "application/x-mpegURL");
    }

    @Operation(summary = "Get HLS video segment")
    @GetMapping("/{videoId}/{resolution}/{segmentName:[a-zA-Z0-9_-]+\\.ts}")
    public ResponseEntity<StreamingResponseBody> getSegment(
            @PathVariable UUID videoId,
            @PathVariable String resolution,
            @PathVariable String segmentName) {
        String objectPath = videoId.toString() + "/hls/" + resolution + "/" + segmentName;
        return streamObject(objectPath, "video/MP2T");
    }

    private ResponseEntity<StreamingResponseBody> streamObject(String objectPath, String contentType) {
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = storageService.getObject(minioConfig.getProcessedBucket(), objectPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                log.warn("Error while streaming object {}: {}", objectPath, e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(responseBody);
    }
}
