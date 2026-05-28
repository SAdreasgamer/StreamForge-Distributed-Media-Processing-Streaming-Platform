package com.streamforge.service;

import com.streamforge.dto.request.CreateUploadSessionRequest;
import com.streamforge.dto.response.UploadSessionResponse;
import com.streamforge.dto.response.VideoResponse;
import com.streamforge.dto.event.VideoUploadedEvent;
import com.streamforge.kafka.UploadEventProducer;
import com.streamforge.exception.InvalidFileTypeException;
import com.streamforge.exception.UploadSessionExpiredException;
import com.streamforge.model.UploadSession;
import com.streamforge.model.Video;
import com.streamforge.model.enums.UploadStatus;
import com.streamforge.model.enums.VideoStatus;
import com.streamforge.repository.UploadSessionRepository;
import com.streamforge.repository.VideoRepository;
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

    private final VideoRepository videoRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final StorageService storageService;
    private final UploadEventProducer uploadEventProducer;

    @Transactional
    public UploadSessionResponse createSession(CreateUploadSessionRequest request) {
        if (!FileValidator.isAllowedContentType(request.contentType())) throw new InvalidFileTypeException(request.contentType());
        Video video = videoRepository.save(Video.builder().title(request.title()).description(request.description())
                .originalFilename("pending").contentType(request.contentType()).status(VideoStatus.PENDING).build());
        UploadSession session = uploadSessionRepository.save(UploadSession.builder().video(video).status(UploadStatus.ACTIVE)
                .contentType(request.contentType()).expiresAt(Instant.now().plus(24, ChronoUnit.HOURS)).build());
        log.info("Upload session created: {} for video: {}", session.getSessionId(), video.getId());
        return new UploadSessionResponse(session.getSessionId(), video.getId(), session.getStatus().name(), session.getExpiresAt(), session.getCreatedAt());
    }

    public UploadSessionResponse getSession(UUID sessionId) {
        UploadSession session = uploadSessionRepository.findById(sessionId).orElseThrow(() -> new UploadSessionExpiredException(sessionId));
        return new UploadSessionResponse(session.getSessionId(), session.getVideo().getId(), session.getStatus().name(), session.getExpiresAt(), session.getCreatedAt());
    }

    @Transactional
    public VideoResponse uploadFile(UUID sessionId, MultipartFile file) {
        FileValidator.validate(file);
        UploadSession session = uploadSessionRepository.findBySessionIdAndStatus(sessionId, UploadStatus.ACTIVE)
                .orElseThrow(() -> new UploadSessionExpiredException(sessionId));
        if (Instant.now().isAfter(session.getExpiresAt())) {
            session.setStatus(UploadStatus.EXPIRED); uploadSessionRepository.save(session); throw new UploadSessionExpiredException(sessionId);
        }
        Video video = session.getVideo();
        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4";
            String storagePath = storageService.uploadRawFile(video.getId(), originalFilename, file.getInputStream(), file.getSize(), file.getContentType());
            video.setOriginalFilename(originalFilename); video.setStoragePath(storagePath);
            video.setFileSizeBytes(file.getSize()); video.setContentType(file.getContentType()); video.setStatus(VideoStatus.UPLOADED);
            videoRepository.save(video);
            session.setStatus(UploadStatus.COMPLETED); session.setFileSizeBytes(file.getSize()); uploadSessionRepository.save(session);
            log.info("File uploaded for video {}: {} ({} bytes)", video.getId(), originalFilename, file.getSize());
            
            UUID videoId = video.getId();
            String title = video.getTitle();
            String desc = video.getDescription();
            String origFilename = video.getOriginalFilename();
            String storagePathVal = video.getStoragePath();
            long fileSize = video.getFileSizeBytes() != null ? video.getFileSizeBytes() : 0L;

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    uploadEventProducer.sendVideoUploaded(new VideoUploadedEvent(
                        videoId,
                        title,
                        desc,
                        origFilename,
                        storagePathVal,
                        fileSize
                    ));
                }
            });
            return new VideoResponse(video.getId(), video.getTitle(), video.getStatus().name(), null, null, null, video.getFileSizeBytes(), video.getCreatedAt());
        } catch (Exception e) {
            video.setStatus(VideoStatus.FAILED); video.setErrorMessage("Upload failed: " + e.getMessage()); videoRepository.save(video);
            throw new com.streamforge.exception.ProcessingException("File upload failed", e);
        }
    }
}
