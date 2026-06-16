package com.shortly.repository;

import com.shortly.model.LinkStatsDaily;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public interface LinkStatsDailyRepository extends ReactiveCrudRepository<LinkStatsDaily, Long> {

    Flux<LinkStatsDaily> findByLinkIdOrderByDateDesc(Long linkId);

    @Query("SELECT * FROM link_stats_daily WHERE link_id = :linkId AND date BETWEEN :start AND :end ORDER BY date")
    Flux<LinkStatsDaily> findByLinkIdBetweenDates(Long linkId, LocalDate start, LocalDate end);

    Mono<LinkStatsDaily> findByLinkIdAndDate(Long linkId, LocalDate date);
}
