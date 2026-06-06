package com.streamforge.kafka;

import com.streamforge.dto.event.ProcessingStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "media.processing.status", groupId = "notification-group")
    public void consumeProcessingStatus(ProcessingStatusEvent event) {
        log.info("Received processing status for WebSocket broadcast. Video: {}, status: {}", 
                event.videoId(), event.status());

        String destination = "/topic/video/" + event.videoId() + "/status";
        messagingTemplate.convertAndSend(destination, event);
        log.info("Successfully broadcast status update to WebSocket client subscription: {}", destination);
    }
}
