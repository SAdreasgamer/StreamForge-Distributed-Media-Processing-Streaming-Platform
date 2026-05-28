package com.streamforge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamforge.exception.ProcessingException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public final class FFmpegUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private FFmpegUtil() {}

    public static JsonNode probe(String ffprobePath, Path videoFile, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffprobePath, "-v", "quiet", "-print_format", "json",
                    "-show_format", "-show_streams", videoFile.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); throw new ProcessingException("FFprobe timed out"); }
            if (process.exitValue() != 0) throw new ProcessingException("FFprobe failed: " + output);
            return MAPPER.readTree(output);
        } catch (ProcessingException e) { throw e;
        } catch (Exception e) { throw new ProcessingException("FFprobe execution failed", e); }
    }

    public static void execute(List<String> command, Duration timeout) {
        try {
            log.info("Executing FFmpeg: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) { log.debug("FFmpeg: {}", line); }
            }
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); throw new ProcessingException("FFmpeg timed out"); }
            if (process.exitValue() != 0) throw new ProcessingException("FFmpeg failed with exit code " + process.exitValue());
            log.info("FFmpeg completed successfully");
        } catch (ProcessingException e) { throw e;
        } catch (Exception e) { throw new ProcessingException("FFmpeg execution failed", e); }
    }
}
