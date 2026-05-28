package com.streamforge.service;

import com.streamforge.config.MinioConfig;
import com.streamforge.config.ProcessingProperties;
import com.streamforge.exception.ProcessingException;
import com.streamforge.model.Video;
import com.streamforge.model.enums.VideoStatus;
import com.streamforge.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingService {

    private final VideoRepository videoRepository;
    private final StorageService storageService;
    private final MetadataService metadataService;
    private final TranscodeService transcodeService;
    private final ThumbnailService thumbnailService;
    private final ProcessingProperties processingProperties;
    private final MinioConfig minioConfig;

    @Async("processingExecutor")
    public CompletableFuture<Void> processVideoAsync(UUID videoId) {
        log.info("Starting async processing for video: {}", videoId);
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ProcessingException("Video not found: " + videoId));
        video.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(video);

        Path workDir = Path.of(processingProperties.getTempDir(), videoId.toString());
        try {
            Path rawVideoFile = storageService.downloadToTemp(minioConfig.getRawBucket(), video.getStoragePath(), workDir.resolve("raw"));
            log.info("Step 1/3: Extracting metadata...");
            metadataService.extractMetadata(video, rawVideoFile);
            log.info("Step 2/3: Transcoding to HLS...");
            transcodeService.transcode(video, rawVideoFile, workDir);
            log.info("Step 3/3: Generating thumbnail...");
            thumbnailService.generateThumbnail(video, rawVideoFile, workDir);

            video.setStatus(VideoStatus.PROCESSED);
            video.setErrorMessage(null);
            videoRepository.save(video);
            log.info("Video processing completed: {}", videoId);
        } catch (Exception e) {
            log.error("Video processing failed for {}: {}", videoId, e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            video.setErrorMessage(e.getMessage());
            videoRepository.save(video);
        } finally { cleanupWorkDir(workDir); }
        return CompletableFuture.completedFuture(null);
    }

    private void cleanupWorkDir(Path workDir) {
        try {
            if (Files.exists(workDir)) {
                Files.walkFileTree(workDir, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
                });
            }
        } catch (IOException e) { log.warn("Failed to clean up {}: {}", workDir, e.getMessage()); }
    }
}
