package com.shortly.service;

import com.shortly.model.Link;
import com.shortly.repository.LinkRepository;
import com.shortly.util.TtlParser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Core service for creating and resolving shortened links.
 *
 * <p>Orchestration flow for creation:
 * <ol>
 *   <li>Parse and validate TTL via {@link TtlParser}</li>
 *   <li>Generate a unique short code via {@link ShortCodeGenerator}</li>
 *   <li>Persist the link to PostgreSQL via {@link LinkRepository}</li>
 *   <li>Cache the link in Redis via {@link RedisCacheService}</li>
 * </ol>
 *
 * <p>Resolution uses a Redis-first pattern: check cache, fall back to DB,
 * and backfill the cache on a miss (read-through caching).
 */
@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final RedisCacheService redisCacheService;

    /**
     * @param linkRepository      the reactive R2DBC link repository
     * @param shortCodeGenerator  the short code generator
     * @param redisCacheService   the Redis cache service
     */
    public LinkService(LinkRepository linkRepository,
                       ShortCodeGenerator shortCodeGenerator,
                       RedisCacheService redisCacheService) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.redisCacheService = redisCacheService;
    }

    /**
     * Creates a new shortened link, persists it, and caches it in Redis.
     *
     * @param originalUrl  the long URL to shorten (must be a valid HTTP/HTTPS URL)
     * @param ttlString    TTL in human-readable format (e.g. {@code "30d"}, {@code "3600"})
     * @param redirectType the redirect type: {@code "301"} or {@code "302"} (defaults to {@code "302"})
     * @return a Mono emitting the saved {@link Link} entity
     */
    public Mono<Link> createLink(String originalUrl, String ttlString, String redirectType) {
        Duration ttl = TtlParser.parse(ttlString);
        String redirect = (redirectType != null && redirectType.equals("301")) ? "301" : "302";

        return shortCodeGenerator.generate()
                .flatMap(code -> {
                    Link link = Link.builder()
                            .shortCode(code)
                            .originalUrl(originalUrl)
                            .redirectType(redirect)
                            .expiresAt(Instant.now().plus(ttl))
                            .createdAt(Instant.now())
                            .build();

                    return linkRepository.save(link)
                            .flatMap(saved -> redisCacheService.cacheLink(
                                    saved.getShortCode(),
                                    saved.getOriginalUrl(),
                                    saved.getRedirectType(),
                                    ttl.getSeconds()
                            ).thenReturn(saved));
                });
    }

    /**
     * Resolves a short code to its original URL via Redis-first lookup.
     *
     * <p>On a cache hit the link is reconstructed from Redis hash fields.
     * On a miss the database is queried and the cache is backfilled.
     *
     * @param shortCode the short code to resolve
     * @return a Mono emitting the {@link Link} if found and active, or empty if not found
     */
    public Mono<Link> resolveLink(String shortCode) {
        return redisCacheService.getLink(shortCode)
                .flatMap(fields -> {
                    Link link = Link.builder()
                            .shortCode(shortCode)
                            .originalUrl(fields.get("url").toString())
                            .redirectType(fields.get("redirect_type").toString())
                            .expiresAt(Instant.ofEpochSecond(
                                    Long.parseLong(fields.get("expires_at").toString())))
                            .build();
                    return Mono.just(link);
                })
                .switchIfEmpty(
                        linkRepository.findByShortCode(shortCode)
                                .flatMap(link -> {
                                    long remainingTtl = link.getExpiresAt().getEpochSecond()
                                            - Instant.now().getEpochSecond();
                                    if (remainingTtl > 0) {
                                        return redisCacheService.cacheLink(
                                                link.getShortCode(),
                                                link.getOriginalUrl(),
                                                link.getRedirectType(),
                                                remainingTtl
                                        ).thenReturn(link);
                                    }
                                    return Mono.just(link);
                                })
                );
    }

    /**
     * Checks whether a link is active (not soft-deleted) and not expired.
     *
     * @param link the link to check
     * @return a Mono emitting {@code true} if active and not expired
     */
    public Mono<Boolean> isActive(Link link) {
        return Mono.just(
                Boolean.TRUE.equals(link.getIsActive())
                        && link.getExpiresAt().isAfter(Instant.now())
        );
    }
}
