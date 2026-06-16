package com.shortly.repository;

import com.shortly.model.LinkStatsDaily;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Reactive R2DBC repository for {@link LinkStatsDaily} daily rollup entities.
 */
@Repository
public interface LinkStatsDailyRepository extends ReactiveCrudRepository<LinkStatsDaily, Long> {

    /**
     * Finds daily stats for a link, ordered by most recent date first.
     *
     * @param linkId the link ID
     * @return a Flux emitting daily stats in descending date order
     */
    Flux<LinkStatsDaily> findByLinkIdOrderByDateDesc(Long linkId);

    /**
     * Finds daily stats for a link within a date range.
     *
     * @param linkId the link ID
     * @param start  the inclusive start date
     * @param end    the inclusive end date
     * @return a Flux emitting stats ordered by date ascending
     */
    @Query("SELECT * FROM link_stats_daily WHERE link_id = :linkId AND date BETWEEN :start AND :end ORDER BY date")
    Flux<LinkStatsDaily> findByLinkIdBetweenDates(Long linkId, LocalDate start, LocalDate end);

    /**
     * Finds the stats record for a specific link and date combination.
     *
     * @param linkId the link ID
     * @param date   the target date
     * @return a Mono emitting the stats record if it exists
     */
    Mono<LinkStatsDaily> findByLinkIdAndDate(Long linkId, LocalDate date);
}
