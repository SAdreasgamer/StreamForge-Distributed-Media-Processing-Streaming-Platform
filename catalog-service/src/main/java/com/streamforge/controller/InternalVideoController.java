package com.streamforge.controller;

import com.streamforge.dto.request.CreateVideoRequest;
import com.streamforge.dto.request.UpdateVideoUploadDetailsRequest;
import com.streamforge.dto.response.StatusResponse;
import com.streamforge.dto.response.VideoCreatedResponse;
import com.streamforge.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/videos")
@RequiredArgsConstructor
public class InternalVideoController {

    private final VideoService videoService;

    @PostMapping
    public VideoCreatedResponse createVideo(@RequestBody CreateVideoRequest request) {
        return videoService.createVideo(request);
    }

    @PostMapping("/{videoId}/uploaded")
    public void markVideoUploaded(
            @PathVariable UUID videoId,
            @RequestBody UpdateVideoUploadDetailsRequest request) {
        videoService.markVideoUploaded(videoId, request);
    }

    @GetMapping("/{videoId}/status")
    public StatusResponse getVideoStatus(@PathVariable UUID videoId) {
        return videoService.getStatus(videoId);
    }
}
