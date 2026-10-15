package com.platform.orderprocessing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration providing typed templates with Jackson JSON serialization.
 */
@Configuration
public class RedisConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, InventoryResponse> inventoryRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, InventoryResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<InventoryResponse> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, InventoryResponse.class);

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
