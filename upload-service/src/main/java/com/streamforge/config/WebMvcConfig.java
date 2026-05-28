package com.streamforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Override default static resource mapping so /api/** routes are
        // never intercepted by the static resource handler
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
