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
 * Orchestrates: validation -> code generation -> DB persistence -> Redis caching.
 */
@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final RedisCacheService redisCacheService;

    public LinkService(LinkRepository linkRepository,
                       ShortCodeGenerator shortCodeGenerator,
                       RedisCacheService redisCacheService) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.redisCacheService = redisCacheService;
    }

    /**
     * Creates a new shortened link.
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
     * Resolves a short code to its original URL.
     * Redis-first: checks cache, falls back to DB, backfills cache on miss.
     */
    public Mono<Link> resolveLink(String shortCode) {
        return redisCacheService.getLink(shortCode)
                .flatMap(fields -> {
                    // Cache hit — rebuild Link from Redis fields
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
                        // Cache miss — query DB
                        linkRepository.findByShortCode(shortCode)
                                .flatMap(link -> {
                                    // Backfill cache
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
     * Checks if a link exists and is not expired.
     */
    public Mono<Boolean> isActive(Link link) {
        return Mono.just(
                Boolean.TRUE.equals(link.getIsActive())
                        && link.getExpiresAt().isAfter(Instant.now())
        );
    }
}
