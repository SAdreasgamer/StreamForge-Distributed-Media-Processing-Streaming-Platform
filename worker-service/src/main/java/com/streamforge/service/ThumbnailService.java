package com.streamforge.service;

import com.streamforge.config.FFmpegProperties;
import com.streamforge.config.ProcessingProperties;
import com.streamforge.exception.ProcessingException;
import com.streamforge.model.Video;
import com.streamforge.repository.VideoRepository;
import com.streamforge.util.FFmpegUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbnailService {

    private final FFmpegProperties ffmpegProperties;
    private final ProcessingProperties processingProperties;
    private final StorageService storageService;
    private final VideoRepository videoRepository;

    @Transactional
    public void generateThumbnail(Video video, Path rawVideoFile, Path workDir) {
        log.info("Generating thumbnail for video: {}", video.getId());
        try {
            Path thumbnailDir = workDir.resolve("thumbnails");
            Files.createDirectories(thumbnailDir);
            Path posterFile = thumbnailDir.resolve("poster.jpg");
            ProcessingProperties.ThumbnailConfig config = processingProperties.getThumbnail();

            String timestamp = config.getTimestamp();
            if (video.getDurationSeconds() != null && video.getDurationSeconds() <= 5.0) {
                timestamp = String.format("00:00:%02d", (int) (video.getDurationSeconds() * 0.25));
            }

            List<String> command = new ArrayList<>(List.of(ffmpegProperties.getPath(),
                    "-i", rawVideoFile.toAbsolutePath().toString(), "-ss", timestamp,
                    "-vframes", "1", "-vf", "scale=" + config.getWidth() + ":" + config.getHeight(),
                    "-q:v", "2", "-y", posterFile.toAbsolutePath().toString()));
            FFmpegUtil.execute(command, Duration.ofSeconds(30));

            String thumbnailPath = video.getId() + "/thumbnails/poster.jpg";
            storageService.uploadProcessedFile(thumbnailPath, posterFile, "image/jpeg");
            video.setThumbnailPath(thumbnailPath);
            videoRepository.save(video);
            log.info("Thumbnail generated for video: {}", video.getId());
        } catch (IOException e) { throw new ProcessingException("Thumbnail generation failed", e); }
    }
}
