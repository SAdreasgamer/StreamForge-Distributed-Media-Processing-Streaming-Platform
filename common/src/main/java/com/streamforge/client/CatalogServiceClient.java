package com.streamforge.client;

import com.streamforge.dto.request.CreateVideoRequest;
import com.streamforge.dto.response.StatusResponse;
import com.streamforge.dto.response.VideoCreatedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "CATALOG-SERVICE")
public interface CatalogServiceClient {

    @PostMapping("/api/internal/videos")
    VideoCreatedResponse createVideo(@RequestBody CreateVideoRequest request);

    @PostMapping("/api/internal/videos/{videoId}/uploaded")
    void markVideoUploaded(
        @PathVariable("videoId") UUID videoId,
        @RequestBody com.streamforge.dto.request.UpdateVideoUploadDetailsRequest request
    );

    @GetMapping("/api/internal/videos/{videoId}/status")
    StatusResponse getVideoStatus(@PathVariable("videoId") java.util.UUID videoId);
}
