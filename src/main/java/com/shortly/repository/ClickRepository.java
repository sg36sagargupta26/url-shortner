package com.shortly.repository;

import com.shortly.model.Click;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive R2DBC repository for {@link Click} entities.
 */
@Repository
public interface ClickRepository extends ReactiveCrudRepository<Click, Long> {

    /**
     * Finds all clicks for a link, ordered by most recent first.
     *
     * @param linkId the link ID
     * @return a Flux emitting clicks in descending chronological order
     */
    Flux<Click> findByLinkIdOrderByClickedAtDesc(Long linkId);

    /**
     * Counts clicks for a link since a given timestamp.
     *
     * @param linkId the link ID
     * @param since  the exclusive lower bound timestamp
     * @return a Mono emitting the click count
     */
    @Query("SELECT COUNT(*) FROM clicks WHERE link_id = :linkId AND clicked_at >= :since")
    Mono<Long> countByLinkIdSince(Long linkId, Instant since);

    /**
     * Deletes all clicks older than the given timestamp.
     * Used by the scheduled cleanup job to enforce the 30-day retention policy.
     *
     * @param before the exclusive upper bound timestamp
     * @return a Mono that completes when deletion is done
     */
    @Query("DELETE FROM clicks WHERE clicked_at < :before")
    Mono<Void> deleteOlderThan(Instant before);
}
