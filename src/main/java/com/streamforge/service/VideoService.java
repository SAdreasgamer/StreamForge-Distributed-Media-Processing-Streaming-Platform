package com.streamforge.service;

import com.streamforge.config.MinioConfig;
import com.streamforge.dto.response.*;
import com.streamforge.exception.VideoNotFoundException;
import com.streamforge.model.Video;
import com.streamforge.model.VideoVariant;
import com.streamforge.model.enums.VideoStatus;
import com.streamforge.repository.VideoRepository;
import com.streamforge.repository.VideoVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ProcessingService processingService;
    private final MinioConfig minioConfig;

    public Page<VideoResponse> listVideos(Pageable pageable) {
        return videoRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toVideoResponse);
    }

    public VideoDetailResponse getVideoDetail(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        List<VariantResponse> variants = videoVariantRepository.findByVideoId(videoId).stream()
                .map(vr -> new VariantResponse(vr.getResolution(), vr.getWidth(), vr.getHeight(), vr.getBitrateKbps(),
                        "/api/stream/" + videoId + "/" + vr.getResolution() + "/playlist.m3u8"))
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

    public PlaybackResponse getPlayback(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStatus() != VideoStatus.PROCESSED)
            throw new IllegalStateException("Video not ready. Status: " + v.getStatus());
        List<VariantResponse> variants = videoVariantRepository.findByVideoId(videoId).stream()
                .map(vr -> new VariantResponse(vr.getResolution(), vr.getWidth(), vr.getHeight(), vr.getBitrateKbps(),
                        "/api/stream/" + videoId + "/" + vr.getResolution() + "/playlist.m3u8"))
                .toList();
        String masterUrl = "/api/stream/" + v.getId() + "/master.m3u8";
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
    public void deleteVideo(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStoragePath() != null)
            storageService.deleteObject(minioConfig.getRawBucket(), v.getStoragePath());
        videoVariantRepository.findByVideoId(videoId)
                .forEach(vr -> storageService.deleteObject(minioConfig.getProcessedBucket(), vr.getManifestPath()));
        if (v.getThumbnailPath() != null)
            storageService.deleteObject(minioConfig.getProcessedBucket(), v.getThumbnailPath());
        videoRepository.delete(v);
        log.info("Deleted video: {}", videoId);
    }

    public String getThumbnailUrl(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getThumbnailPath() == null)
            throw new VideoNotFoundException("Thumbnail not available: " + videoId);
        return storageService.generatePresignedUrl(minioConfig.getProcessedBucket(), v.getThumbnailPath(), URL_EXPIRY);
    }

    @Transactional
    public StatusResponse reprocessVideo(UUID videoId) {
        Video v = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
        if (v.getStoragePath() == null)
            throw new IllegalStateException("Video has no uploaded file to process");
        v.setStatus(VideoStatus.UPLOADED);
        v.setErrorMessage(null);
        videoRepository.save(v);
        processingService.processVideoAsync(videoId);
        log.info("Reprocessing triggered for video: {}", videoId);
        return new StatusResponse(v.getId(), v.getStatus().name(), null, v.getUpdatedAt());
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
