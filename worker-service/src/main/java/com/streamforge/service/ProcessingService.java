package com.streamforge.service;

import com.streamforge.config.MinioConfig;
import com.streamforge.config.ProcessingProperties;
import com.streamforge.dto.VideoProcessingContext;
import com.streamforge.dto.event.ProcessingStatusEvent;
import com.streamforge.exception.ProcessingException;
import com.streamforge.kafka.WorkerEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingService {

    private final StorageService storageService;
    private final MetadataService metadataService;
    private final TranscodeService transcodeService;
    private final ThumbnailService thumbnailService;
    private final ProcessingProperties processingProperties;
    private final MinioConfig minioConfig;
    private final WorkerEventProducer eventProducer;

    public void processVideo(UUID videoId, String storagePath, String originalFilename) {
        log.info("Starting processing context for video: {}", videoId);

        VideoProcessingContext context = new VideoProcessingContext(videoId, originalFilename, storagePath);

        // 1. Emit PROCESSING start event
        eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                videoId,
                "PROCESSING",
                "Initiating processing pipeline",
                null,
                null,
                null,
                null,
                Instant.now()
        ));

        Path workDir = Path.of(processingProperties.getTempDir(), videoId.toString());
        try {
            Path rawVideoFile = storageService.downloadToTemp(minioConfig.getRawBucket(), storagePath, workDir.resolve("raw"));

            // 2. Step 1/3: Extract metadata
            log.info("Step 1/3: Extracting metadata...");
            metadataService.extractMetadata(context, rawVideoFile);

            ProcessingStatusEvent.VideoMetadata metadata = new ProcessingStatusEvent.VideoMetadata(
                    context.getDurationSeconds(),
                    context.getWidth(),
                    context.getHeight(),
                    context.getFps(),
                    context.getCodec(),
                    context.getBitrateKbps(),
                    context.getAudioCodec()
            );
            eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                    videoId,
                    "METADATA_EXTRACTED",
                    "Video metadata extracted",
                    metadata,
                    null,
                    null,
                    null,
                    Instant.now()
            ));

            // 3. Step 2/3: Transcoding
            log.info("Step 2/3: Transcoding to HLS...");
            transcodeService.transcode(context, rawVideoFile, workDir);

            eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                    videoId,
                    "TRANSCODE_COMPLETED",
                    "HLS transcoding completed",
                    null,
                    context.getVariants(),
                    null,
                    null,
                    Instant.now()
            ));

            // 4. Step 3/3: Thumbnail generation
            log.info("Step 3/3: Generating thumbnail...");
            thumbnailService.generateThumbnail(context, rawVideoFile, workDir);

            eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                    videoId,
                    "THUMBNAIL_COMPLETED",
                    "Thumbnail generated",
                    null,
                    null,
                    context.getThumbnailPath(),
                    null,
                    Instant.now()
            ));

            // 5. Emit final success PROCESSED event
            eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                    videoId,
                    "PROCESSED",
                    "Video processing completed successfully",
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
            ));
            log.info("Video processing completed for video ID: {}", videoId);
        } catch (Exception e) {
            log.error("Video processing failed for video ID {}: {}", videoId, e.getMessage(), e);

            // Emit FAILED event
            eventProducer.sendProcessingStatus(new ProcessingStatusEvent(
                    videoId,
                    "FAILED",
                    "Processing failed: " + e.getMessage(),
                    null,
                    null,
                    null,
                    e.getMessage(),
                    Instant.now()
            ));
            throw new ProcessingException("Video processing failed: " + e.getMessage(), e);
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    private void cleanupWorkDir(Path workDir) {
        try {
            if (Files.exists(workDir)) {
                Files.walkFileTree(workDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to clean up {}: {}", workDir, e.getMessage());
        }
    }
}
