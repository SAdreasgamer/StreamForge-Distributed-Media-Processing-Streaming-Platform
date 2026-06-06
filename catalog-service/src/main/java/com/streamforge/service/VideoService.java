package com.streamforge.service;

import com.streamforge.config.MinioConfig;
import com.streamforge.dto.event.VideoUploadedEvent;
import com.streamforge.dto.request.CreateVideoRequest;
import com.streamforge.dto.request.UpdateVideoUploadDetailsRequest;
import com.streamforge.dto.response.*;
import com.streamforge.exception.VideoNotFoundException;
import com.streamforge.model.Video;
import com.streamforge.model.VideoVariant;
import com.streamforge.model.enums.VideoStatus;
import com.streamforge.repository.VideoRepository;
import com.streamforge.repository.VideoVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private static final int URL_EXPIRY = 900;
    private final VideoRepository videoRepository;
    private final VideoVariantRepository videoVariantRepository;
    private final StorageService storageService;
    private final MinioConfig minioConfig;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Cacheable(value = "videos-list", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<VideoResponse> listVideos(Pageable pageable) {
        log.info("Fetching videos list from database for page: {}", pageable.getPageNumber());
        return videoRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toVideoResponse);
    }

    @Cacheable(value = "video-detail", key = "#videoId")
    public VideoDetailResponse getVideoDetail(UUID videoId) {
        log.info("Fetching video detail from database for ID: {}", videoId);
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        List<VariantResponse> variants = videoVariantRepository.findByVideoId(videoId).stream()
                .map(vr -> new VariantResponse(vr.getResolution(), vr.getWidth(), vr.getHeight(), vr.getBitrateKbps(),
                        storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), vr.getManifestPath(),
                                URL_EXPIRY)))
                .toList();
        String thumbUrl = v.getThumbnailPath() != null
                ? storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), v.getThumbnailPath(),
                        URL_EXPIRY)
                : null;
        return new VideoDetailResponse(v.getId(), v.getTitle(), v.getDescription(), v.getStatus().name(),
                v.getOriginalFilename(),
                v.getFileSizeBytes(), v.getDurationSeconds(), v.getWidth(), v.getHeight(), v.getFps(), v.getCodec(),
                v.getBitrateKbps(), v.getAudioCodec(), thumbUrl, v.getErrorMessage(), variants, v.getCreatedAt(),
                v.getUpdatedAt());
    }

    @Cacheable(value = "video-playback", key = "#videoId")
    public PlaybackResponse getPlayback(UUID videoId) {
        log.info("Fetching video playback from database for ID: {}", videoId);
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStatus() != VideoStatus.PROCESSED)
            throw new IllegalStateException("Video not ready. Status: " + v.getStatus());
        List<VariantResponse> variants = videoVariantRepository.findByVideoId(videoId).stream()
                .map(vr -> new VariantResponse(vr.getResolution(), vr.getWidth(), vr.getHeight(), vr.getBitrateKbps(),
                        storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), vr.getManifestPath(),
                                URL_EXPIRY)))
                .toList();
        String masterUrl = storageService.generatePresignedUrl(minioConfig.getProcessedBucket(),
                v.getId() + "/hls/master.m3u8", URL_EXPIRY);
        String thumbUrl = v.getThumbnailPath() != null
                ? storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), v.getThumbnailPath(),
                        URL_EXPIRY)
                : null;
        return new PlaybackResponse(v.getId(), v.getTitle(), masterUrl, variants, thumbUrl);
    }

    public StatusResponse getStatus(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        return new StatusResponse(v.getId(), v.getStatus().name(), v.getErrorMessage(), v.getUpdatedAt());
    }

    @Transactional
    @CacheEvict(value = {"videos-list", "video-detail", "video-playback"}, allEntries = true)
    public void deleteVideo(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStoragePath() != null)
            storageService.deleteObject(minioConfig.getRawBucket(), v.getStoragePath());
        videoVariantRepository.findByVideoId(videoId)
                .forEach(vr -> storageService.deleteObject(minioConfig.getProcessedBucket(), vr.getManifestPath()));
        if (v.getThumbnailPath() != null)
            storageService.deleteObject(minioConfig.getProcessedBucket(), v.getThumbnailPath());
        videoRepository.delete(v);
        log.info("Deleted video and evicted cache: {}", videoId);
    }

    public String getThumbnailUrl(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getThumbnailPath() == null)
            throw new VideoNotFoundException("Thumbnail not available: " + videoId);
        return storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), v.getThumbnailPath(), URL_EXPIRY);
    }

    @Transactional
    @CacheEvict(value = {"videos-list", "video-detail", "video-playback"}, allEntries = true)
    public StatusResponse reprocessVideo(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStoragePath() == null)
            throw new IllegalStateException("Video has no uploaded file to process");
        v.setStatus(VideoStatus.UPLOADED);
        v.setErrorMessage(null);
        Video saved = videoRepository.save(v);

        // Emit VideoUploadedEvent to Kafka
        VideoUploadedEvent event = new VideoUploadedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getOriginalFilename(),
                saved.getStoragePath(),
                saved.getFileSizeBytes() != null ? saved.getFileSizeBytes() : 0L
        );
        kafkaTemplate.send("media.uploaded", event.videoId().toString(), event);
        log.info("Reprocessing triggered and event emitted for video: {}", videoId);
        return new StatusResponse(saved.getId(), saved.getStatus().name(), null, saved.getUpdatedAt());
    }

    @Transactional
    @CacheEvict(value = "videos-list", allEntries = true)
    public VideoCreatedResponse createVideo(CreateVideoRequest request) {
        Video video = Video.builder()
                .title(request.title())
                .description(request.description())
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .status(VideoStatus.PENDING)
                .build();
        Video saved = videoRepository.save(video);
        log.info("Created video record: {}", saved.getId());
        return new VideoCreatedResponse(saved.getId());
    }

    @Transactional
    @CacheEvict(value = {"video-detail", "videos-list"}, key = "#videoId", beforeInvocation = false)
    public void markVideoUploaded(UUID videoId, UpdateVideoUploadDetailsRequest request) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        v.setOriginalFilename(request.originalFilename());
        v.setStoragePath(request.storagePath());
        v.setFileSizeBytes(request.fileSizeBytes());
        v.setStatus(VideoStatus.UPLOADED);
        videoRepository.save(v);
        log.info("Marked video {} as UPLOADED", videoId);
    }

    private VideoResponse toVideoResponse(Video v) {
        String res = (v.getWidth() != null && v.getHeight() != null) ? v.getWidth() + "x" + v.getHeight() : null;
        String thumbUrl = v.getThumbnailPath() != null
                ? storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), v.getThumbnailPath(),
                        URL_EXPIRY)
                : null;
        return new VideoResponse(v.getId(), v.getTitle(), v.getStatus().name(), thumbUrl, v.getDurationSeconds(), res,
                v.getFileSizeBytes(), v.getCreatedAt());
    }
}
