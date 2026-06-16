package com.shortly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for reactive (Lettuce) connections.
 *
 * <p>Defines two beans:
 * <ul>
 *   <li>{@link ReactiveRedisConnectionFactory} — connects to the configured Redis host/port</li>
 *   <li>{@link ReactiveRedisTemplate} — string-serialized template for all Redis operations</li>
 * </ul>
 *
 * <p>Both keys and values use {@link StringRedisSerializer} since we store
 * simple strings, counters, and JSON snippets rather than serialised objects.
 */
@Configuration
public class RedisConfig {

    @Value("${shortly.rate-limit.max-requests:1}")
    private int maxRequests;

    @Value("${shortly.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /**
     * Creates a reactive Lettuce connection factory to the configured Redis instance.
     *
     * @param host Redis host (from {@code spring.data.redis.host}, default {@code localhost})
     * @param port Redis port (from {@code spring.data.redis.port}, default {@code 6379})
     * @return a configured {@link ReactiveRedisConnectionFactory}
     */
    @Bean
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new LettuceConnectionFactory(host, port);
    }

    /**
     * Creates a reactive Redis template with string serialization for both keys and values.
     *
     * @param factory the reactive connection factory
     * @return a fully configured {@link ReactiveRedisTemplate}
     */
    @Bean
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
