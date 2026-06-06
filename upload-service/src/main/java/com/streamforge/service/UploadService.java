package com.streamforge.service;

import com.streamforge.client.CatalogServiceClient;
import com.streamforge.dto.request.CreateUploadSessionRequest;
import com.streamforge.dto.request.CreateVideoRequest;
import com.streamforge.dto.request.UpdateVideoUploadDetailsRequest;
import com.streamforge.dto.response.UploadSessionResponse;
import com.streamforge.dto.response.VideoCreatedResponse;
import com.streamforge.dto.response.VideoResponse;
import com.streamforge.dto.event.VideoUploadedEvent;
import com.streamforge.kafka.UploadEventProducer;
import com.streamforge.exception.InvalidFileTypeException;
import com.streamforge.exception.UploadSessionExpiredException;
import com.streamforge.model.UploadSession;
import com.streamforge.model.enums.UploadStatus;
import com.streamforge.repository.UploadSessionRepository;
import com.streamforge.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadSessionRepository uploadSessionRepository;
    private final StorageService storageService;
    private final UploadEventProducer uploadEventProducer;
    private final CatalogServiceClient catalogServiceClient;

    @Transactional
    public UploadSessionResponse createSession(CreateUploadSessionRequest request) {
        if (!FileValidator.isAllowedContentType(request.contentType())) {
            throw new InvalidFileTypeException(request.contentType());
        }

        // Call Catalog Service via Feign to create the video record
        CreateVideoRequest createVideoRequest = new CreateVideoRequest(
                request.title(),
                request.description(),
                "pending",
                request.contentType()
        );
        VideoCreatedResponse videoCreated = catalogServiceClient.createVideo(createVideoRequest);
        UUID videoId = videoCreated.videoId();

        UploadSession session = uploadSessionRepository.save(UploadSession.builder()
                .videoId(videoId)
                .status(UploadStatus.ACTIVE)
                .contentType(request.contentType())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build());

        log.info("Upload session created: {} for video: {}", session.getSessionId(), videoId);
        return new UploadSessionResponse(
                session.getSessionId(),
                videoId,
                session.getStatus().name(),
                session.getExpiresAt(),
                session.getCreatedAt()
        );
    }

    public UploadSessionResponse getSession(UUID sessionId) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new UploadSessionExpiredException(sessionId));
        return new UploadSessionResponse(
                session.getSessionId(),
                session.getVideoId(),
                session.getStatus().name(),
                session.getExpiresAt(),
                session.getCreatedAt()
        );
    }

    @Transactional
    public VideoResponse uploadFile(UUID sessionId, MultipartFile file) {
        FileValidator.validate(file);
        UploadSession session = uploadSessionRepository.findBySessionIdAndStatus(sessionId, UploadStatus.ACTIVE)
                .orElseThrow(() -> new UploadSessionExpiredException(sessionId));

        if (Instant.now().isAfter(session.getExpiresAt())) {
            session.setStatus(UploadStatus.EXPIRED);
            uploadSessionRepository.save(session);
            throw new UploadSessionExpiredException(sessionId);
        }

        UUID videoId = session.getVideoId();
        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4";
            String storagePath = storageService.uploadRawFile(
                    videoId,
                    originalFilename,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );

            // Call Catalog Service via Feign to update the video details and mark as UPLOADED
            UpdateVideoUploadDetailsRequest updateRequest = new UpdateVideoUploadDetailsRequest(
                    originalFilename,
                    storagePath,
                    file.getSize()
            );
            catalogServiceClient.markVideoUploaded(videoId, updateRequest);

            // Complete session
            session.setStatus(UploadStatus.COMPLETED);
            session.setFileSizeBytes(file.getSize());
            uploadSessionRepository.save(session);

            log.info("File uploaded for video {}: {} ({} bytes)", videoId, originalFilename, file.getSize());

            // Emit event to Kafka
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    uploadEventProducer.sendVideoUploaded(new VideoUploadedEvent(
                            videoId,
                            null, // Title/description are managed by catalog-service now
                            null,
                            originalFilename,
                            storagePath,
                            file.getSize()
                    ));
                }
            });

            return new VideoResponse(
                    videoId,
                    null, // Title can be fetched from catalog-service if needed, but not required in upload response
                    "UPLOADED",
                    null,
                    null,
                    null,
                    file.getSize(),
                    Instant.now()
            );
        } catch (Exception e) {
            session.setStatus(UploadStatus.FAILED);
            uploadSessionRepository.save(session);
            log.error("File upload failed for session: {}", sessionId, e);
            throw new com.streamforge.exception.ProcessingException("File upload failed", e);
        }
    }
}
