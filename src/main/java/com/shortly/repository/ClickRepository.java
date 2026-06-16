package com.shortly.repository;

import com.shortly.model.Click;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface ClickRepository extends ReactiveCrudRepository<Click, Long> {

    Flux<Click> findByLinkIdOrderByClickedAtDesc(Long linkId);

    @Query("SELECT COUNT(*) FROM clicks WHERE link_id = :linkId AND clicked_at >= :since")
    Mono<Long> countByLinkIdSince(Long linkId, Instant since);

    @Query("DELETE FROM clicks WHERE clicked_at < :before")
    Mono<Void> deleteOlderThan(Instant before);
}
