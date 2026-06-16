package com.shortly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token bucket rate limiter using Redis INCR + EXPIRE.
 */
@Service
public class RateLimiterService {

    private final RedisCacheService redisCacheService;
    private final int maxRequests;
    private final int windowSeconds;

    public RateLimiterService(RedisCacheService redisCacheService,
                               @Value("${shortly.rate-limit.max-requests:1}") int maxRequests,
                               @Value("${shortly.rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisCacheService = redisCacheService;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks if the given IP is allowed to make a request.
     * Returns true if allowed (under limit), false if rate limited.
     */
    public Mono<Boolean> isAllowed(String ip) {
        return redisCacheService.isRateLimited(ip, maxRequests, windowSeconds)
                .map(limited -> !limited);
    }

    /**
     * Returns the remaining time in seconds until the rate limit resets.
     * Approximate, based on the window duration.
     */
    public int getWindowSeconds() {
        return windowSeconds;
    }
}
