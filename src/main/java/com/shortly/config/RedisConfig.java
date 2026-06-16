package com.shortly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for reactive (Lettuce) connections.
 *
 * <p>Spring Boot auto-configures the {@link ReactiveRedisConnectionFactory}
 * from {@code spring.data.redis.*} properties. This class provides a custom
 * {@link ReactiveRedisTemplate} with string serialization for both keys and values.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a reactive Redis template with string serialization for both keys and values.
     * Marked {@link Primary} to override Spring Boot's auto-configured template.
     *
     * @param factory the auto-configured reactive connection factory
     * @return a fully configured {@link ReactiveRedisTemplate}
     */
    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, String> context =
                RedisSerializationContext.<String, String>newSerializationContext(new StringRedisSerializer())
                        .value(new StringRedisSerializer())
                        .hashKey(new StringRedisSerializer())
                        .hashValue(new StringRedisSerializer())
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
