package com.shortly.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortly.model.Click;
import com.shortly.model.Link;
import com.shortly.repository.ClickRepository;
import com.shortly.service.GeoLocationService.GeoResult;
import com.shortly.service.UserAgentParserService.UAProfile;
import com.shortly.util.IpHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Processes click events asynchronously so the redirect response is never
 * blocked by analytics work.
 *
 * <p>Flow:
 * <ol>
 *   <li>Redirect controller calls {@link #recordClick(Link, String, String, String)}</li>
 *   <li>Geo-location and User-Agent parsing happen inline (fast, cached)</li>
 *   <li>Redis counters are incremented immediately (real-time dashboard)</li>
 *   <li>Click entity is persisted to PostgreSQL (durable storage)</li>
 * </ol>
 *
 * <p>All work is reactive and non-blocking. The redirect response is sent
 * before the database write completes via {@code Mono.defer}.
 */
@Service
public class ClickProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ClickProcessingService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ClickRepository clickRepository;
    private final GeoLocationService geoLocationService;
    private final UserAgentParserService uaParserService;
    private final RedisCacheService redisCacheService;

    /**
     * @param clickRepository    the click repository for persistence
     * @param geoLocationService the MaxMind geo-location service
     * @param uaParserService    the User-Agent parser service
     * @param redisCacheService  the Redis cache service for counter updates
     */
    public ClickProcessingService(ClickRepository clickRepository,
                                   GeoLocationService geoLocationService,
                                   UserAgentParserService uaParserService,
                                   RedisCacheService redisCacheService) {
        this.clickRepository = clickRepository;
        this.geoLocationService = geoLocationService;
        this.uaParserService = uaParserService;
        this.redisCacheService = redisCacheService;
    }

    /**
     * Records a click event for the given link and request metadata.
     *
     * <p>This method fires the analytics pipeline and returns immediately
     * (the caller does not wait for the database write).
     *
     * @param link      the resolved link entity (must have a valid ID)
     * @param clientIp  the visitor's IP address
     * @param userAgent the raw User-Agent header value
     * @param referrer  the raw Referer header value
     */
    public void recordClick(Link link, String clientIp, String userAgent, String referrer) {
        // Defer all work so the caller doesn't block on analytics
        Mono.defer(() -> processClick(link, clientIp, userAgent, referrer))
                .subscribe(
                        click -> log.debug("Click recorded: linkId={}, country={}",
                                click.getLinkId(), click.getCountry()),
                        error -> log.error("Failed to record click for linkId={}", link.getId(), error)
                );
    }

    /**
     * Processes a single click: geolocation, UA parsing, counter updates, persistence.
     *
     * @param link      the resolved link
     * @param clientIp  the visitor's IP
     * @param userAgent the User-Agent header
     * @param referrer  the Referer header
     * @return a Mono emitting the persisted Click entity
     */
    private Mono<Click> processClick(Link link, String clientIp, String userAgent, String referrer) {
        if (link.getId() == null) {
            return Mono.empty();
        }

        Long linkId = link.getId();

        // 1. Geo-lookup
        GeoResult geo = geoLocationService.lookup(clientIp);

        // 2. UA parsing
        UAProfile ua = uaParserService.parse(userAgent);

        // 3. Build metadata JSON
        String metadata = buildMetadata(ua);

        // 4. Persist click
        Click click = Click.builder()
                .linkId(linkId)
                .ipHash(IpHashUtil.hash(clientIp))
                .country(geo.country())
                .city(geo.city())
                .userAgent(userAgent)
                .referrer(referrer)
                .metadata(metadata)
                .build();

        return clickRepository.save(click)
                .flatMap(saved -> {
                    // 5. Update Redis real-time counters (fire-and-forget via subscribe)
                    updateRedisCounters(linkId, geo, ua, clientIp, referrer);
                    return Mono.just(saved);
                });
    }

    /**
     * Increments all Redis real-time counters for a click.
     *
     * @param linkId    the link ID
     * @param geo       the geo-location result
     * @param ua        the UA parse result
     * @param clientIp  the raw IP for unique visitor counting
     * @param referrer  the referrer URL
     */
    private void updateRedisCounters(Long linkId, GeoResult geo, UAProfile ua,
                                      String clientIp, String referrer) {
        redisCacheService.incrementTotalClicks(linkId).subscribe();
        redisCacheService.addUniqueVisitor(linkId, IpHashUtil.hash(clientIp)).subscribe();

        if (!geo.country().equals("XX")) {
            redisCacheService.incrementCategoryCount(
                    RedisCacheService.clicksCountryPrefix(), linkId, geo.country()).subscribe();
        }
        redisCacheService.incrementCategoryCount(
                RedisCacheService.clicksDevicePrefix(), linkId, ua.device()).subscribe();
        redisCacheService.incrementCategoryCount(
                RedisCacheService.clicksBrowserPrefix(), linkId, ua.browser()).subscribe();
        redisCacheService.incrementCategoryCount(
                RedisCacheService.clicksOsPrefix(), linkId, ua.os()).subscribe();

        if (referrer != null && !referrer.isBlank()) {
            String truncatedReferrer = referrer.length() > 500 ? referrer.substring(0, 500) : referrer;
            redisCacheService.incrementCategoryCount(
                    RedisCacheService.clicksReferrerPrefix(), linkId, truncatedReferrer).subscribe();
        }
    }

    /**
     * Builds a JSON metadata string from the UA profile.
     *
     * @param ua the parsed User-Agent profile
     * @return a JSON string like {@code {"device":"iPhone","browser":"Safari","os":"iOS"}}
     */
    private String buildMetadata(UAProfile ua) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "device", ua.device(),
                    "browser", ua.browser(),
                    "os", ua.os()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise UA metadata", e);
            return "{}";
        }
    }
}
