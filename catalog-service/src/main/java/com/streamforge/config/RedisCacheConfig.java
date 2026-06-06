package com.streamforge.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Construct custom ObjectMapper with Java Time support and default typing
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Register PageImpl deserializer module
        SimpleModule pageModule = new SimpleModule();
        pageModule.addDeserializer(PageImpl.class, new PageImplDeserializer());
        objectMapper.registerModule(pageModule);

        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration: JSON serializer, 5-minute TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        // Custom configs for detail and playback: 15-minute TTL
        Map<String, RedisCacheConfiguration> customConfigs = new HashMap<>();
        customConfigs.put("video-detail", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        customConfigs.put("video-playback", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        customConfigs.put("videos-list", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(customConfigs)
                .build();
    }

    public static class PageImplDeserializer extends JsonDeserializer<PageImpl<?>> {
        private static final ObjectMapper CLEAN_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public PageImpl<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            JsonNode node = CLEAN_MAPPER.readTree(p);

            List<?> content = new ArrayList<>();
            JsonNode contentNode = node.get("content");
            if (contentNode != null) {
                content = mapper.readValue(contentNode.traverse(mapper), List.class);
            }

            long totalElements = 0;
            if (node.has("totalElements")) {
                totalElements = node.get("totalElements").asLong();
            }

            int pageNumber = 0;
            int pageSize = 20;
            if (node.has("pageable")) {
                JsonNode pageableNode = node.get("pageable");
                if (pageableNode.has("pageNumber")) {
                    pageNumber = pageableNode.get("pageNumber").asInt();
                }
                if (pageableNode.has("pageSize")) {
                    pageSize = pageableNode.get("pageSize").asInt();
                }
            } else {
                if (node.has("number")) {
                    pageNumber = node.get("number").asInt();
                }
                if (node.has("size")) {
                    pageSize = node.get("size").asInt();
                }
            }

            return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), totalElements);
        }
    }
}
