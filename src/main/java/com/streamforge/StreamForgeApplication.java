package com.streamforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StreamForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamForgeApplication.class, args);
    }
}
