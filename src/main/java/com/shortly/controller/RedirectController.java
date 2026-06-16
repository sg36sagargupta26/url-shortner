package com.shortly.controller;

import com.shortly.dto.ErrorResponse;
import com.shortly.model.Link;
import com.shortly.service.LinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * REST controller for resolving short codes to original URLs.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code GET /{shortCode}} — redirect to the original URL</li>
 * </ul>
 *
 * <p>Response codes:
 * <ul>
 *   <li>302/301 — redirect to the original URL (based on link's redirect type)</li>
 *   <li>404 — short code not found</li>
 *   <li>410 — link has expired</li>
 * </ul>
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    /**
     * @param linkService the link resolution service
     */
    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Resolves a short code and redirects to the original URL.
     *
     * @param shortCode the short code from the URL path
     * @return a redirect (302/301), 404, or 410 response
     */
    @GetMapping("/{shortCode}")
    public Mono<ResponseEntity<?>> redirect(@PathVariable String shortCode) {
        return linkService.resolveLink(shortCode)
                .flatMap(link -> linkService.isActive(link)
                        .flatMap(active -> {
                            if (!active) {
                                ErrorResponse error = ErrorResponse.expired(
                                        "This link has expired.",
                                        link.getExpiresAt());
                                return Mono.just(ResponseEntity.status(HttpStatus.GONE).body(error));
                            }

                            HttpStatus status = "301".equals(link.getRedirectType())
                                    ? HttpStatus.MOVED_PERMANENTLY
                                    : HttpStatus.FOUND; // 302

                            return Mono.just(ResponseEntity.status(status)
                                    .location(URI.create(link.getOriginalUrl()))
                                    .build());
                        })
                )
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.notFound("Short code not found: " + shortCode))
                ));
    }
}
