package com.streamforge.service;

import com.streamforge.config.FFmpegProperties;
import com.streamforge.config.ProcessingProperties;
import com.streamforge.exception.ProcessingException;
import com.streamforge.model.Video;
import com.streamforge.model.VideoVariant;
import com.streamforge.repository.VideoVariantRepository;
import com.streamforge.util.FFmpegUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscodeService {

    private final FFmpegProperties ffmpegProperties;
    private final ProcessingProperties processingProperties;
    private final StorageService storageService;
    private final VideoVariantRepository videoVariantRepository;

    public void transcode(Video video, Path rawVideoFile, Path workDir) {
        log.info("Starting transcoding for video: {}", video.getId());
        for (ProcessingProperties.ResolutionConfig res : processingProperties.getResolutions()) {
            transcodeResolution(video, rawVideoFile, workDir, res);
        }
        generateMasterManifest(video, workDir, processingProperties.getResolutions());
        log.info("Transcoding completed for video: {}", video.getId());
    }

    private void transcodeResolution(Video video, Path rawVideoFile, Path workDir, ProcessingProperties.ResolutionConfig res) {
        log.info("Transcoding video {} to {}", video.getId(), res.getLabel());
        Path outputDir = workDir.resolve(res.getLabel());
        try { Files.createDirectories(outputDir); } catch (IOException e) { throw new ProcessingException("Failed to create dir: " + outputDir, e); }

        List<String> command = new ArrayList<>(List.of(
                ffmpegProperties.getPath(), "-i", rawVideoFile.toAbsolutePath().toString(),
                "-vf", "scale=" + res.getWidth() + ":" + res.getHeight(),
                "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k",
                "-b:v", res.getBitrate(), "-maxrate", res.getBitrate(), "-bufsize", (parseBitrateKbps(res.getBitrate()) * 2) + "k",
                "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", outputDir.resolve("segment_%03d.ts").toAbsolutePath().toString(),
                "-y", outputDir.resolve("playlist.m3u8").toAbsolutePath().toString()));

        FFmpegUtil.execute(command, Duration.ofSeconds(ffmpegProperties.getTimeoutSeconds()));

        String baseStoragePath = video.getId().toString() + "/hls/" + res.getLabel();
        uploadDirectoryContents(outputDir, baseStoragePath);

        VideoVariant variant = VideoVariant.builder().video(video).resolution(res.getLabel())
                .width(res.getWidth()).height(res.getHeight()).bitrateKbps(parseBitrateKbps(res.getBitrate()))
                .manifestPath(baseStoragePath + "/playlist.m3u8").storagePath(baseStoragePath)
                .fileSizeBytes(calculateDirSize(outputDir)).build();
        videoVariantRepository.save(variant);
    }

    private void generateMasterManifest(Video video, Path workDir, List<ProcessingProperties.ResolutionConfig> resolutions) {
        StringBuilder manifest = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n\n");
        for (ProcessingProperties.ResolutionConfig res : resolutions) {
            manifest.append(String.format("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d\n",
                    parseBitrateKbps(res.getBitrate()) * 1000, res.getWidth(), res.getHeight()));
            manifest.append(res.getLabel()).append("/playlist.m3u8\n\n");
        }
        try {
            Path masterFile = workDir.resolve("master.m3u8");
            Files.writeString(masterFile, manifest.toString());
            storageService.uploadProcessedFile(video.getId() + "/hls/master.m3u8", masterFile, "application/vnd.apple.mpegurl");
        } catch (IOException e) { throw new ProcessingException("Failed to generate master manifest", e); }
    }

    private void uploadDirectoryContents(Path dir, String baseStoragePath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    String ct = file.toString().endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
                    storageService.uploadProcessedFile(baseStoragePath + "/" + file.getFileName(), file, ct);
                }
            }
        } catch (IOException e) { throw new ProcessingException("Failed to upload transcoded files", e); }
    }

    private int parseBitrateKbps(String bitrate) { return Integer.parseInt(bitrate.replaceAll("[^0-9]", "")); }

    private long calculateDirSize(Path dir) {
        try { return Files.walk(dir).filter(Files::isRegularFile).mapToLong(f -> { try { return Files.size(f); } catch (IOException e) { return 0; } }).sum();
        } catch (IOException e) { return 0; }
    }
}
