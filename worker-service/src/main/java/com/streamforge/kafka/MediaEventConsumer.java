package com.streamforge.kafka;

import com.streamforge.dto.event.VideoUploadedEvent;
import com.streamforge.model.ProcessingJob;
import com.streamforge.model.Video;
import com.streamforge.model.enums.JobStatus;
import com.streamforge.model.enums.JobType;
import com.streamforge.repository.ProcessingJobRepository;
import com.streamforge.repository.VideoRepository;
import com.streamforge.service.ProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaEventConsumer {

    private final ProcessingService processingService;
    private final ProcessingJobRepository processingJobRepository;
    private final VideoRepository videoRepository;

    @KafkaListener(topics = "media.uploaded", groupId = "media-processing-group")
    public void consumeVideoUploaded(VideoUploadedEvent event) {
        UUID videoId = event.videoId();
        log.info("Received VideoUploadedEvent for video ID: {} (title: {})", videoId, event.title());
        
        String idempotencyKey = videoId + ":" + JobType.TRANSCODE;

        // 1. Enforce Idempotency
        Optional<ProcessingJob> existingJobOpt = processingJobRepository.findByIdempotencyKey(idempotencyKey);
        if (existingJobOpt.isPresent()) {
            ProcessingJob existingJob = existingJobOpt.get();
            if (existingJob.getStatus() == JobStatus.RUNNING || existingJob.getStatus() == JobStatus.COMPLETED) {
                log.info("Discarding duplicate message. Processing job for video ID: {} is currently {}", videoId, existingJob.getStatus());
                return;
            }
            // If the job failed previously, we will retry it. Update status to RUNNING.
            existingJob.setStatus(JobStatus.RUNNING);
            existingJob.setRetryCount(existingJob.getRetryCount() + 1);
            processingJobRepository.save(existingJob);
        } else {
            // New job, create record with RUNNING status
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));
            ProcessingJob newJob = ProcessingJob.builder()
                    .video(video)
                    .jobType(JobType.TRANSCODE)
                    .status(JobStatus.RUNNING)
                    .idempotencyKey(idempotencyKey)
                    .retryCount(0)
                    .build();
            processingJobRepository.save(newJob);
        }

        try {
            // 2. Trigger the video processing pipeline (synchronously on the Kafka listener thread)
            processingService.processVideo(videoId);

            // 3. Mark job as COMPLETED
            updateJobStatus(idempotencyKey, JobStatus.COMPLETED, null);
        } catch (Exception e) {
            log.error("Error while processing video {}: {}", videoId, e.getMessage());
            // 4. Mark job as FAILED
            updateJobStatus(idempotencyKey, JobStatus.FAILED, e.getMessage());
            throw e; // Reraise exception so Spring Kafka's ErrorHandler can trigger retries/DLQ routing
        }
    }

    private void updateJobStatus(String idempotencyKey, JobStatus status, String errorMessage) {
        processingJobRepository.findByIdempotencyKey(idempotencyKey).ifPresent(job -> {
            job.setStatus(status);
            job.setErrorMessage(errorMessage);
            processingJobRepository.save(job);
        });
    }

    @KafkaListener(topics = "media.uploaded.DLT", groupId = "media-processing-group-dlt")
    public void consumeVideoUploadedDlt(VideoUploadedEvent event) {
        log.error("Received message in Dead Letter Queue (DLQ) for video ID: {} (title: {})", event.videoId(), event.title());
    }
}
