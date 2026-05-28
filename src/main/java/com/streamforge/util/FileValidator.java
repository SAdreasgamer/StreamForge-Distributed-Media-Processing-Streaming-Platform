package com.streamforge.util;

import com.streamforge.exception.InvalidFileTypeException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public final class FileValidator {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo", "video/x-matroska");
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;

    private FileValidator() {}

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidFileTypeException("empty");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType))
            throw new InvalidFileTypeException(contentType != null ? contentType : "unknown");
        if (file.getSize() > MAX_FILE_SIZE)
            throw new InvalidFileTypeException("File size " + file.getSize() + " exceeds maximum of 2GB");
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
    }
}
