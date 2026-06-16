package com.shortly.service;

import com.shortly.dto.AnalyticsResponse;
import com.shortly.model.Link;
import com.shortly.model.LinkStatsDaily;
import com.shortly.repository.LinkStatsDailyRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Merges real-time Redis counters with historical PostgreSQL rollups
 * to serve the analytics endpoint.
 *
 * <p>Redis provides live click counts and category breakdowns (sub-millisecond).
 * PostgreSQL provides historical daily trends (from the {@code link_stats_daily} table).
 */
@Service
public class AnalyticsService {

    private final RedisCacheService redisCacheService;
    private final LinkStatsDailyRepository statsRepository;

    /**
     * @param redisCacheService the Redis cache service for real-time counters
     * @param statsRepository   the daily stats repository for historical data
     */
    public AnalyticsService(RedisCacheService redisCacheService,
                             LinkStatsDailyRepository statsRepository) {
        this.redisCacheService = redisCacheService;
        this.statsRepository = statsRepository;
    }

    /**
     * Builds a complete analytics response for a link, merging real-time
     * Redis counters with historical daily breakdowns.
     *
     * @param link the link entity (must have a valid ID)
     * @return a Mono emitting the fully populated {@link AnalyticsResponse}
     */
    public Mono<AnalyticsResponse> getAnalytics(Link link) {
        Long linkId = link.getId();

        Mono<Long> totalClicks = redisCacheService.getTotalClicks(linkId);
        Mono<Long> uniqueVisitors = redisCacheService.getUniqueVisitors(linkId);

        Mono<List<AnalyticsResponse.CategoryCount>> byCountry =
                loadCategoryCounts(RedisCacheService.clicksCountryPrefix(), linkId);
        Mono<List<AnalyticsResponse.CategoryCount>> byDevice =
                loadCategoryCounts(RedisCacheService.clicksDevicePrefix(), linkId);
        Mono<List<AnalyticsResponse.CategoryCount>> byBrowser =
                loadCategoryCounts(RedisCacheService.clicksBrowserPrefix(), linkId);
        Mono<List<AnalyticsResponse.CategoryCount>> byOs =
                loadCategoryCounts(RedisCacheService.clicksOsPrefix(), linkId);
        Mono<List<AnalyticsResponse.CategoryCount>> byReferrer =
                loadCategoryCounts(RedisCacheService.clicksReferrerPrefix(), linkId);

        // Historical daily breakdown from Postgres (last 30 days)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        Mono<List<AnalyticsResponse.DailyBreakdown>> dailyBreakdown =
                statsRepository.findByLinkIdBetweenDates(linkId, startDate, endDate)
                        .map(stats -> new AnalyticsResponse.DailyBreakdown(
                                stats.getDate().toString(),
                                stats.getTotalClicks(),
                                stats.getUniqueVisitors()
                        ))
                        .collectList()
                        .map(list -> {
                            list.sort(Comparator.comparing(
                                    AnalyticsResponse.DailyBreakdown::date).reversed());
                            return list;
                        });

        return Mono.zip(totalClicks, uniqueVisitors,
                        byCountry, byDevice, byBrowser, byOs, byReferrer, dailyBreakdown)
                .map(tuple -> new AnalyticsResponse(
                        link.getShortCode(),
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5(),
                        tuple.getT6(),
                        tuple.getT7(),
                        tuple.getT8()
                ));
    }

    /**
     * Loads category counts from a Redis hash and converts them to a sorted list.
     *
     * @param prefix the Redis key prefix
     * @param linkId the link ID
     * @return a Mono emitting a list of {@link AnalyticsResponse.CategoryCount},
     *         sorted by count descending
     */
    private Mono<List<AnalyticsResponse.CategoryCount>> loadCategoryCounts(String prefix, Long linkId) {
        return redisCacheService.getCategoryCounts(prefix, linkId)
                .map(map -> {
                    List<AnalyticsResponse.CategoryCount> list = new ArrayList<>();
                    map.forEach((key, count) ->
                            list.add(new AnalyticsResponse.CategoryCount(key, count)));
                    list.sort(Comparator.comparingLong(
                            AnalyticsResponse.CategoryCount::count).reversed());
                    return list;
                })
                .defaultIfEmpty(List.of());
    }
}
