package com.shortly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token bucket rate limiter backed by Redis INCR + EXPIRE.
 *
 * <p>Each IP gets its own counter key with a sliding window. When the first
 * request arrives the key is created with the window TTL. Subsequent
 * requests increment the counter; if it exceeds {@code maxRequests} the
 * request is rejected with HTTP 429.
 */
@Service
public class RateLimiterService {

    private final RedisCacheService redisCacheService;
    private final int maxRequests;
    private final int windowSeconds;

    /**
     * @param redisCacheService the Redis cache service used for counter operations
     * @param maxRequests       maximum allowed requests per window (default 1)
     * @param windowSeconds     window duration in seconds (default 60)
     */
    public RateLimiterService(RedisCacheService redisCacheService,
                               @Value("${shortly.rate-limit.max-requests:1}") int maxRequests,
                               @Value("${shortly.rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisCacheService = redisCacheService;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks whether the given IP is allowed to make a request.
     *
     * @param ip the client IP address
     * @return a Mono emitting {@code true} if under the limit, {@code false} if rate-limited
     */
    public Mono<Boolean> isAllowed(String ip) {
        return redisCacheService.isRateLimited(ip, maxRequests, windowSeconds)
                .map(limited -> !limited);
    }

    /**
     * Returns the configured rate-limit window duration in seconds.
     * Useful for populating the {@code Retry-After} header in 429 responses.
     *
     * @return the window duration in seconds
     */
    public int getWindowSeconds() {
        return windowSeconds;
    }
}
