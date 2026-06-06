package com.streamforge.kafka;

import com.streamforge.dto.event.ProcessingStatusEvent;
import com.streamforge.model.Video;
import com.streamforge.model.VideoVariant;
import com.streamforge.model.enums.VideoStatus;
import com.streamforge.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventConsumer {

    private final VideoRepository videoRepository;
    private final CacheManager cacheManager;

    @KafkaListener(topics = "media.processing.status", groupId = "catalog-group")
    @Transactional
    public void consumeProcessingStatus(ProcessingStatusEvent event) {
        log.info("Received processing status event for video {}: status={}, message={}", 
                event.videoId(), event.status(), event.message());

        Video video = videoRepository.findById(event.videoId()).orElse(null);
        if (video == null) {
            log.error("Video not found in catalog database: {}", event.videoId());
            return;
        }

        boolean updated = false;

        // Map status to VideoStatus if applicable
        switch (event.status()) {
            case "PROCESSING" -> {
                video.setStatus(VideoStatus.PROCESSING);
                video.setErrorMessage(null);
                updated = true;
            }
            case "PROCESSED" -> {
                video.setStatus(VideoStatus.PROCESSED);
                video.setErrorMessage(null);
                updated = true;
            }
            case "FAILED" -> {
                video.setStatus(VideoStatus.FAILED);
                video.setErrorMessage(event.errorMessage());
                updated = true;
            }
        }

        // Update metadata if present in event
        if (event.metadata() != null) {
            ProcessingStatusEvent.VideoMetadata meta = event.metadata();
            video.setDurationSeconds(meta.durationSeconds());
            video.setWidth(meta.width());
            video.setHeight(meta.height());
            video.setFps(meta.fps());
            video.setCodec(meta.codec());
            video.setBitrateKbps(meta.bitrateKbps());
            video.setAudioCodec(meta.audioCodec());
            updated = true;
            log.info("Updated metadata for video {}: {}s, {}x{}", video.getId(), meta.durationSeconds(), meta.width(), meta.height());
        }

        // Update variants if present in event
        if (event.variants() != null && !event.variants().isEmpty()) {
            video.getVariants().clear();
            for (ProcessingStatusEvent.VariantInfo variantInfo : event.variants()) {
                VideoVariant variant = VideoVariant.builder()
                        .video(video)
                        .resolution(variantInfo.resolution())
                        .width(variantInfo.width())
                        .height(variantInfo.height())
                        .bitrateKbps(variantInfo.bitrateKbps())
                        .manifestPath(variantInfo.manifestPath())
                        .storagePath(variantInfo.storagePath())
                        .fileSizeBytes(variantInfo.fileSizeBytes())
                        .build();
                video.getVariants().add(variant);
            }
            updated = true;
            log.info("Updated HLS variants for video {}: total {} variants", video.getId(), event.variants().size());
        }

        // Update thumbnail path if present
        if (event.thumbnailPath() != null) {
            video.setThumbnailPath(event.thumbnailPath());
            updated = true;
            log.info("Updated thumbnail path for video {}: {}", video.getId(), event.thumbnailPath());
        }

        if (updated) {
            videoRepository.save(video);
            evictCache(video.getId());
        }
    }

    private void evictCache(UUID videoId) {
        log.info("Evicting Redis cache for video {}", videoId);
        try {
            Cache detailCache = cacheManager.getCache("video-detail");
            if (detailCache != null) {
                detailCache.evict(videoId);
            }
            Cache playbackCache = cacheManager.getCache("video-playback");
            if (playbackCache != null) {
                playbackCache.evict(videoId);
            }
            Cache listCache = cacheManager.getCache("videos-list");
            if (listCache != null) {
                listCache.clear(); // Clear all pages of the listing cache
            }
        } catch (Exception e) {
            log.error("Failed to evict Redis cache for video {}: {}", videoId, e.getMessage());
        }
    }
}
