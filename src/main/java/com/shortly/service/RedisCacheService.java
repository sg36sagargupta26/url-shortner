package com.shortly.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates all Redis operations: shortlink caching, rate limiting,
 * and real-time analytics counters.
 *
 * <p>Each functional area uses a dedicated key prefix to avoid collisions:
 * <ul>
 *   <li>{@code shortlink:*} — cached link metadata (hash, TTL-bound)</li>
 *   <li>{@code ratelimit:*} — per-IP rate limiter counters (string, window-bound)</li>
 *   <li>{@code clicks:*}:{@code linkId} — click counters and HyperLogLog unique visitors</li>
 * </ul>
 */
@Service
public class RedisCacheService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String SHORTLINK_PREFIX = "shortlink:";
    private static final String RATELIMIT_PREFIX = "ratelimit:";
    private static final String CLICKS_TOTAL_PREFIX = "clicks:total:";
    private static final String CLICKS_UNIQUE_PREFIX = "clicks:unique:";
    private static final String CLICKS_COUNTRY_PREFIX = "clicks:country:";
    private static final String CLICKS_DEVICE_PREFIX = "clicks:device:";
    private static final String CLICKS_BROWSER_PREFIX = "clicks:browser:";
    private static final String CLICKS_OS_PREFIX = "clicks:os:";
    private static final String CLICKS_REFERRER_PREFIX = "clicks:referrer:";

    /**
     * @param redisTemplate the configured reactive Redis template
     */
    public RedisCacheService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Shortlink caching ──

    /**
     * Caches a shortened link's metadata as a Redis hash with a TTL.
     *
     * @param shortCode     the short code key
     * @param url           the original URL
     * @param redirectType  the HTTP redirect type ({@code "301"} or {@code "302"})
     * @param ttlSeconds    time-to-live in seconds for both the hash and its expiry
     * @return a Mono emitting {@code true} once the hash is stored and TTL set
     */
    public Mono<Boolean> cacheLink(String shortCode, String url, String redirectType,
                                    long ttlSeconds) {
        String key = SHORTLINK_PREFIX + shortCode;
        Map<String, String> fields = new HashMap<>();
        fields.put("url", url);
        fields.put("redirect_type", redirectType);
        fields.put("expires_at", String.valueOf(System.currentTimeMillis() / 1000 + ttlSeconds));

        return redisTemplate.opsForHash().putAll(key, fields)
                .then(redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds)));
    }

    /**
     * Retrieves a cached link's fields from Redis.
     *
     * @param shortCode the short code to look up
     * @return a Mono emitting the field map if the key exists, or empty if absent
     */
    public Mono<Map<Object, Object>> getLink(String shortCode) {
        return redisTemplate.opsForHash().entries(SHORTLINK_PREFIX + shortCode)
                .collectMap(entry -> entry.getKey(), entry -> entry.getValue())
                .filter(map -> !map.isEmpty());
    }

    /**
     * Deletes a cached link entry from Redis.
     *
     * @param shortCode the short code key to remove
     * @return a Mono emitting {@code true} if the key was deleted, {@code false} if absent
     */
    public Mono<Boolean> deleteLink(String shortCode) {
        return redisTemplate.delete(SHORTLINK_PREFIX + shortCode)
                .map(count -> count > 0);
    }

    // ── Rate limiting ──

    /**
     * Checks whether an IP has exceeded the request limit within the current window.
     * Uses an atomic INCR + EXPIRE pattern on a Redis string key.
     *
     * @param ip            the client IP address
     * @param maxRequests   maximum allowed requests in the window
     * @param windowSeconds window duration in seconds
     * @return a Mono emitting {@code true} if rate-limited, {@code false} if allowed
     */
    public Mono<Boolean> isRateLimited(String ip, int maxRequests, int windowSeconds) {
        String key = RATELIMIT_PREFIX + ip;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(false);
                    }
                    return Mono.just(count > (long) maxRequests);
                });
    }

    // ── Click counters ──

    /**
     * Atomically increments the total click counter for a link.
     *
     * @param linkId the link's database ID
     * @return a Mono emitting the new counter value
     */
    public Mono<Long> incrementTotalClicks(Long linkId) {
        return redisTemplate.opsForValue().increment(CLICKS_TOTAL_PREFIX + linkId);
    }

    /**
     * Returns the current total click count for a link.
     *
     * @param linkId the link's database ID
     * @return a Mono emitting the count, or {@code 0} if absent
     */
    public Mono<Long> getTotalClicks(Long linkId) {
        return redisTemplate.opsForValue().get(CLICKS_TOTAL_PREFIX + linkId)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    /**
     * Adds a visitor identifier to the link's HyperLogLog set for unique counting.
     *
     * @param linkId    the link's database ID
     * @param visitorId a unique visitor identifier (e.g. hashed IP)
     * @return a Mono emitting {@code 1} if the internal register was altered
     */
    public Mono<Long> addUniqueVisitor(Long linkId, String visitorId) {
        return redisTemplate.opsForHyperLogLog().add(CLICKS_UNIQUE_PREFIX + linkId, visitorId);
    }

    /**
     * Estimates the number of unique visitors for a link via HyperLogLog.
     *
     * @param linkId the link's database ID
     * @return a Mono emitting the estimated unique count
     */
    public Mono<Long> getUniqueVisitors(Long linkId) {
        return redisTemplate.opsForHyperLogLog().size(CLICKS_UNIQUE_PREFIX + linkId);
    }

    /**
     * Increments a category counter within a Redis hash.
     *
     * @param prefix   the Redis key prefix (e.g. {@code clicks:country:})
     * @param linkId   the link's database ID
     * @param category the category name to increment (e.g. {@code "US"})
     * @return a Mono emitting the new count for that category
     */
    public Mono<Long> incrementCategoryCount(String prefix, Long linkId, String category) {
        return redisTemplate.opsForHash().increment(prefix + linkId, category, 1);
    }

    /**
     * Retrieves all category counts from a Redis hash.
     *
     * @param prefix the Redis key prefix
     * @param linkId the link's database ID
     * @return a Mono emitting a map of category name to count
     */
    public Mono<Map<String, Long>> getCategoryCounts(String prefix, Long linkId) {
        return redisTemplate.opsForHash().entries(prefix + linkId)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> Long.parseLong(entry.getValue().toString())
                );
    }

    // ── Prefix accessors ──

    /** @return the Redis key prefix for country counters */
    public static String clicksCountryPrefix() { return CLICKS_COUNTRY_PREFIX; }

    /** @return the Redis key prefix for device counters */
    public static String clicksDevicePrefix() { return CLICKS_DEVICE_PREFIX; }

    /** @return the Redis key prefix for browser counters */
    public static String clicksBrowserPrefix() { return CLICKS_BROWSER_PREFIX; }

    /** @return the Redis key prefix for OS counters */
    public static String clicksOsPrefix() { return CLICKS_OS_PREFIX; }

    /** @return the Redis key prefix for referrer counters */
    public static String clicksReferrerPrefix() { return CLICKS_REFERRER_PREFIX; }

    /** @return the Redis key prefix for total click counters */
    public static String clicksTotalPrefix() { return CLICKS_TOTAL_PREFIX; }

    /** @return the Redis key prefix for unique visitor HyperLogLog sets */
    public static String clicksUniquePrefix() { return CLICKS_UNIQUE_PREFIX; }
}
