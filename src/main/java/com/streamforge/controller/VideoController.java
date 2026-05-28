package com.streamforge.controller;

import com.streamforge.dto.response.*;
import com.streamforge.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Videos", description = "Video browsing, playback, and management")
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @Operation(summary = "List all videos")
    @GetMapping
    public ResponseEntity<Page<VideoResponse>> listVideos(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(videoService.listVideos(pageable));
    }

    @Operation(summary = "Get video details")
    @GetMapping("/{id}")
    public ResponseEntity<VideoDetailResponse> getVideoDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(videoService.getVideoDetail(id));
    }

    @Operation(summary = "Get playback URLs")
    @GetMapping("/{id}/playback")
    public ResponseEntity<PlaybackResponse> getPlayback(@PathVariable UUID id) {
        return ResponseEntity.ok(videoService.getPlayback(id));
    }

    @Operation(summary = "Get processing status")
    @GetMapping("/{id}/status")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(videoService.getStatus(id));
    }

    @Operation(summary = "Get thumbnail")
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Void> getThumbnail(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(videoService.getThumbnailUrl(id))).build();
    }

    @Operation(summary = "Delete video")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable UUID id) {
        videoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reprocess a failed video")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<StatusResponse> reprocessVideo(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(videoService.reprocessVideo(id));
    }
}
