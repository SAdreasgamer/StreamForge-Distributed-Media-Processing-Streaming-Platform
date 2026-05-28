package com.streamforge.service;

import com.streamforge.config.MinioConfig;
import com.streamforge.exception.ProcessingException;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public String uploadRawFile(UUID videoId, String filename, InputStream stream, long size, String contentType) {
        String objectPath = videoId.toString() + "/" + filename;
        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(minioConfig.getRawBucket())
                    .object(objectPath).stream(stream, size, -1).contentType(contentType).build());
            log.info("Uploaded raw file to {}/{}", minioConfig.getRawBucket(), objectPath);
            return objectPath;
        } catch (Exception e) {
            throw new ProcessingException("Failed to upload file: " + objectPath, e);
        }
    }

    public String uploadProcessedFile(String objectPath, Path localFile, String contentType) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder().bucket(minioConfig.getProcessedBucket())
                    .object(objectPath).filename(localFile.toAbsolutePath().toString()).contentType(contentType)
                    .build());
            log.info("Uploaded processed file to {}/{}", minioConfig.getProcessedBucket(), objectPath);
            return objectPath;
        } catch (Exception e) {
            throw new ProcessingException("Failed to upload processed file: " + objectPath, e);
        }
    }

    public Path downloadToTemp(String bucket, String objectPath, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            String filename = objectPath.substring(objectPath.lastIndexOf('/') + 1);
            Path targetFile = targetDir.resolve(filename);
            try (InputStream stream = minioClient
                    .getObject(GetObjectArgs.builder().bucket(bucket).object(objectPath).build())) {
                Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded {}/{} to {}", bucket, objectPath, targetFile);
            return targetFile;
        } catch (Exception e) {
            throw new ProcessingException("Failed to download file: " + objectPath, e);
        }
    }

    public String generatePresignedUrl(String bucket, String objectPath, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(io.minio.GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET).bucket(bucket).object(objectPath).expiry(expirySeconds).build());
        } catch (Exception e) {
            throw new ProcessingException("Failed to generate presigned URL: " + objectPath, e);
        }
    }

    public InputStream getObject(String bucket, String objectPath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectPath).build());
        } catch (Exception e) {
            throw new ProcessingException("Failed to get object: " + objectPath, e);
        }
    }

    public void deleteObject(String bucket, String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectPath).build());
            log.info("Deleted {}/{}", bucket, objectPath);
        } catch (Exception e) {
            log.warn("Failed to delete {}/{}: {}", bucket, objectPath, e.getMessage());
        }
    }

    public String getRawBucket() {
        return minioConfig.getRawBucket();
    }

    public String getProcessedBucket() {
        return minioConfig.getProcessedBucket();
    }
}
