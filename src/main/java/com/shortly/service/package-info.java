/**
 * Business logic services, caching, analytics, and scheduled jobs.
 *
 * <p>This package contains the core application logic organised by concern:
 *
 * <dl>
 *   <dt>Core</dt>
 *   <dd>{@link com.shortly.service.LinkService} —
 *       create and resolve shortened links (Redis-first with DB fallback)</dd>
 *   <dd>{@link com.shortly.service.ShortCodeGenerator} —
 *       Base62 short code generation with collision retry</dd>
 *   <dt>Infrastructure</dt>
 *   <dd>{@link com.shortly.service.RedisCacheService} —
 *       key-value cache, rate limiter counters, and real-time analytics counters</dd>
 *   <dd>{@link com.shortly.service.RateLimiterService} —
 *       IP-based token bucket rate limiter</dd>
 *   <dt>Analytics</dt>
 *   <dd>{@link com.shortly.service.ClickProcessingService} —
 *       asynchronous click recording pipeline (geo, UA, counters, persistence)</dd>
 *   <dd>{@link com.shortly.service.AnalyticsService} —
 *       merges real-time Redis counters with historical Postgres rollups</dd>
 *   <dt>Enrichment</dt>
 *   <dd>{@link com.shortly.service.GeoLocationService} —
 *       MaxMind GeoLite2 IP-to-location lookup</dd>
 *   <dd>{@link com.shortly.service.UserAgentParserService} —
 *       uap-java User-Agent parsing with in-memory cache</dd>
 *   <dt>Scheduling</dt>
 *   <dd>{@link com.shortly.service.ExpiryCleanupJob} —
 *       nightly click purge (30-day retention) and daily rollup</dd>
 * </dl>
 */
package com.shortly.service;
