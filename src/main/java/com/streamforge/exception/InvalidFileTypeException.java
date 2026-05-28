package com.streamforge.exception;

public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String contentType) {
        super("Invalid file type: " + contentType + ". Allowed: video/mp4, video/webm, video/quicktime, video/x-msvideo, video/x-matroska");
    }
}
