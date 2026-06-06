package com.streamforge.controller;

import com.streamforge.dto.request.CreateUploadSessionRequest;
import com.streamforge.dto.response.UploadSessionResponse;
import com.streamforge.dto.response.VideoResponse;
import com.streamforge.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Upload", description = "Video upload operations")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "Create upload session")
    @PostMapping("/sessions")
    public ResponseEntity<UploadSessionResponse> createSession(@Valid @RequestBody CreateUploadSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.createSession(request));
    }

    @Operation(summary = "Get upload session status")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<UploadSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(uploadService.getSession(sessionId));
    }

    @Operation(summary = "Upload video file")
    @PostMapping(value = "/{sessionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoResponse> uploadFile(@PathVariable UUID sessionId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(uploadService.uploadFile(sessionId, file));
    }
}
