package com.streamforge.kafka;

import com.streamforge.dto.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public NewTopic mediaUploadedTopic() {
        return TopicBuilder.name("media.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    }

    public void sendVideoUploaded(VideoUploadedEvent event) {
        log.info("Initiating push of VideoUploadedEvent for video ID: {}", event.videoId());
        try {
            kafkaTemplate.send("media.uploaded", event.videoId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish VideoUploadedEvent for video {}: {}", event.videoId(), ex.getMessage());
                    } else {
                        log.info("Successfully published VideoUploadedEvent for video {} on partition {}", 
                                event.videoId(), result.getRecordMetadata().partition());
                    }
                });
        } catch (Exception e) {
            log.error("Error while initiating VideoUploadedEvent push for video {}: {}", event.videoId(), e.getMessage());
        }
    }
}
