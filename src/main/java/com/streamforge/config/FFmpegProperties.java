package com.streamforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ffmpeg")
@Data
public class FFmpegProperties {

    private String path;
    private String ffprobePath;
    private int timeoutSeconds = 600;
}
