package com.shortly.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates all Redis operations: shortlink caching, rate limiting, and analytics counters.
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

    public RedisCacheService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Shortlink caching ──

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

    public Mono<Map<Object, Object>> getLink(String shortCode) {
        return redisTemplate.opsForHash().entries(SHORTLINK_PREFIX + shortCode)
                .collectMap(entry -> entry.getKey(), entry -> entry.getValue())
                .filter(map -> !map.isEmpty());
    }

    public Mono<Boolean> deleteLink(String shortCode) {
        return redisTemplate.delete(SHORTLINK_PREFIX + shortCode)
                .map(count -> count > 0);
    }

    // ── Rate limiting ──

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

    public Mono<Long> incrementTotalClicks(Long linkId) {
        return redisTemplate.opsForValue().increment(CLICKS_TOTAL_PREFIX + linkId);
    }

    public Mono<Long> getTotalClicks(Long linkId) {
        return redisTemplate.opsForValue().get(CLICKS_TOTAL_PREFIX + linkId)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    public Mono<Long> addUniqueVisitor(Long linkId, String visitorId) {
        return redisTemplate.opsForHyperLogLog().add(CLICKS_UNIQUE_PREFIX + linkId, visitorId);
    }

    public Mono<Long> getUniqueVisitors(Long linkId) {
        return redisTemplate.opsForHyperLogLog().size(CLICKS_UNIQUE_PREFIX + linkId);
    }

    public Mono<Long> incrementCategoryCount(String prefix, Long linkId, String category) {
        return redisTemplate.opsForHash().increment(prefix + linkId, category, 1);
    }

    public Mono<Map<String, Long>> getCategoryCounts(String prefix, Long linkId) {
        return redisTemplate.opsForHash().entries(prefix + linkId)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> Long.parseLong(entry.getValue().toString())
                );
    }

    // ── Prefix accessors for analytics ──

    public static String clicksCountryPrefix() { return CLICKS_COUNTRY_PREFIX; }
    public static String clicksDevicePrefix() { return CLICKS_DEVICE_PREFIX; }
    public static String clicksBrowserPrefix() { return CLICKS_BROWSER_PREFIX; }
    public static String clicksOsPrefix() { return CLICKS_OS_PREFIX; }
    public static String clicksReferrerPrefix() { return CLICKS_REFERRER_PREFIX; }
    public static String clicksTotalPrefix() { return CLICKS_TOTAL_PREFIX; }
    public static String clicksUniquePrefix() { return CLICKS_UNIQUE_PREFIX; }
}
