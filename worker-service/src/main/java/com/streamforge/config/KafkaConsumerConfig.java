package com.streamforge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        // Automatically publishes failed messages to <original-topic-name>.DLT on exhaust
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        
        // 3 Max Attempts (1 initial + 2 retries) with a 2-second initial backoff and 2.0x multiplier
        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
        backOff.setMaxAttempts(3);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Log warnings when retrying
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Media processing failed (attempt {}/3), retrying... Error: {}", 
                    deliveryAttempt, ex.getMessage());
        });
        
        return errorHandler;
    }
}
