package com.streamforge.exception;

import java.util.UUID;

public class VideoNotFoundException extends RuntimeException {
    public VideoNotFoundException(UUID videoId) { super("Video not found: " + videoId); }
    public VideoNotFoundException(String message) { super(message); }
}
