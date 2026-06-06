package com.streamforge.kafka;

import com.streamforge.dto.event.ProcessingStatusEvent;
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
public class WorkerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public NewTopic mediaProcessingStatusTopic() {
        return TopicBuilder.name("media.processing.status")
                .partitions(3)
                .replicas(1)
                .build();
    }

    public void sendProcessingStatus(ProcessingStatusEvent event) {
        log.info("Publishing ProcessingStatusEvent for video ID: {}, status: {}", event.videoId(), event.status());
        try {
            kafkaTemplate.send("media.processing.status", event.videoId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish ProcessingStatusEvent for video {}: {}", event.videoId(), ex.getMessage());
                        } else {
                            log.info("Successfully published ProcessingStatusEvent for video {} on partition {}", 
                                    event.videoId(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("Error while publishing ProcessingStatusEvent for video {}: {}", event.videoId(), e.getMessage());
        }
    }
}
