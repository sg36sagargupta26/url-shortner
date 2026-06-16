package com.shortly.controller;

import com.shortly.dto.ErrorResponse;
import com.shortly.model.Link;
import com.shortly.service.ClickProcessingService;
import com.shortly.service.LinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

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
 *
 * <p>Every successful redirect triggers an asynchronous click-recording
 * pipeline via {@link ClickProcessingService} — the redirect response is
 * never blocked by analytics work.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;
    private final ClickProcessingService clickProcessingService;

    /**
     * @param linkService            the link resolution service
     * @param clickProcessingService the asynchronous click recording service
     */
    public RedirectController(LinkService linkService,
                               ClickProcessingService clickProcessingService) {
        this.linkService = linkService;
        this.clickProcessingService = clickProcessingService;
    }

    /**
     * Resolves a short code and redirects to the original URL.
     * Records a click event asynchronously (non-blocking).
     *
     * @param shortCode  the short code from the URL path
     * @param clientIp   the client IP from {@code X-Forwarded-For} header
     * @param userAgent  the User-Agent header value
     * @param referrer   the Referer header value
     * @return a redirect (302/301), 404, or 410 response
     */
    @GetMapping("/{shortCode}")
    public Mono<ResponseEntity<?>> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Forwarded-For", defaultValue = "127.0.0.1") String clientIp,
            @RequestHeader(value = "User-Agent", defaultValue = "") String userAgent,
            @RequestHeader(value = "Referer", defaultValue = "") String referrer) {

        return linkService.resolveLink(shortCode)
                .flatMap(link -> linkService.isActive(link)
                        .flatMap(active -> {
                            if (!active) {
                                ErrorResponse error = ErrorResponse.expired(
                                        "This link has expired.",
                                        link.getExpiresAt());
                                return Mono.just(ResponseEntity.status(HttpStatus.GONE).body(error));
                            }

                            String ip = clientIp.contains(",")
                                    ? clientIp.split(",")[0].trim() : clientIp.trim();

                            // Fire-and-forget: click recording happens async
                            clickProcessingService.recordClick(link, ip, userAgent, referrer);

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
