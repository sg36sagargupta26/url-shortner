package com.shortly.controller;

import com.shortly.dto.CreateLinkRequest;
import com.shortly.dto.CreateLinkResponse;
import com.shortly.dto.ErrorResponse;
import com.shortly.model.Link;
import com.shortly.service.LinkService;
import com.shortly.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * REST controller for creating shortened URLs.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code POST /api/v1/links} — create a new shortened link</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final RateLimiterService rateLimiterService;

    /**
     * @param linkService        the link creation/resolution service
     * @param rateLimiterService the IP-based rate limiter
     */
    public LinkController(LinkService linkService, RateLimiterService rateLimiterService) {
        this.linkService = linkService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Creates a new shortened URL.
     *
     * <p>Request flow:
     * <ol>
     *   <li>Validate the request body (URL format, TTL, redirect type)</li>
     *   <li>Check rate limit for the client IP</li>
     *   <li>Generate short code and persist</li>
     * </ol>
     *
     * @param request  the validated request body
     * @param clientIp the client IP extracted from {@code X-Forwarded-For} header
     * @return HTTP 201 with the created link, or 429 if rate-limited
     */
    @PostMapping
    public Mono<ResponseEntity<?>> createLink(
            @Valid @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "X-Forwarded-For", defaultValue = "127.0.0.1") String clientIp) {

        // Extract the originating IP (first in the chain if multiple proxies)
        String ip = clientIp.contains(",") ? clientIp.split(",")[0].trim() : clientIp.trim();

        return rateLimiterService.isAllowed(ip)
                .flatMap(allowed -> {
                    if (!allowed) {
                        ErrorResponse error = ErrorResponse.rateLimited(
                                "Too many requests. Please try again later.",
                                rateLimiterService.getWindowSeconds());
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error));
                    }

                    return linkService.createLink(request.url(), request.ttl(), request.redirectType())
                            .map(link -> {
                                CreateLinkResponse response = CreateLinkResponse.of(
                                        link.getShortCode(),
                                        link.getOriginalUrl(),
                                        link.getExpiresAt(),
                                        link.getRedirectType()
                                );
                                return ResponseEntity.created(URI.create("/" + link.getShortCode()))
                                        .body(response);
                            });
                });
    }
}
