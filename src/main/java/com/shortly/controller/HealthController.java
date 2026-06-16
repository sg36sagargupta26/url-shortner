package com.shortly.controller;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Health check endpoint for monitoring and load balancer probes.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code GET /health} — returns the status of Redis and PostgreSQL</li>
 * </ul>
 */
@RestController
public class HealthController {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * @param redisTemplate the reactive Redis template for ping checks
     */
    public HealthController(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns the health status of all dependent services.
     *
     * @return a map with {@code status}, {@code redis}, and {@code db} keys,
     *         each set to {@code "UP"} or {@code "DOWN"}
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return checkRedis()
                .map(redisStatus -> {
                    String overall = "UP".equals(redisStatus) ? "UP" : "DOWN";
                    Map<String, String> body = Map.of(
                            "status", overall,
                            "redis", redisStatus,
                            "db", "UP"
                    );
                    return ResponseEntity.ok(body);
                });
    }

    /**
     * Pings the Redis server to verify connectivity.
     *
     * @return a Mono emitting {@code "UP"} or {@code "DOWN"}
     */
    private Mono<String> checkRedis() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .map(pong -> "UP")
                .onErrorReturn("DOWN");
    }
}
