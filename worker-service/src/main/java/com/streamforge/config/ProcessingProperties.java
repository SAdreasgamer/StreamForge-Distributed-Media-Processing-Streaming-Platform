package com.streamforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "processing")
@Data
public class ProcessingProperties {

    private String tempDir;
    private List<ResolutionConfig> resolutions;
    private ThumbnailConfig thumbnail;

    @Data
    public static class ResolutionConfig {
        private String label;
        private int width;
        private int height;
        private String bitrate;
    }

    @Data
    public static class ThumbnailConfig {
        private int width;
        private int height;
        private String timestamp;
    }
}
