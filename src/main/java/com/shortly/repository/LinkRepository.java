package com.shortly.repository;

import com.shortly.model.Link;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for {@link Link} entities.
 */
@Repository
public interface LinkRepository extends ReactiveCrudRepository<Link, Long> {

    /**
     * Finds a link by its unique short code.
     *
     * @param shortCode the short code to look up
     * @return a Mono emitting the {@link Link} if found, or empty
     */
    Mono<Link> findByShortCode(String shortCode);

    /**
     * Checks whether a short code already exists in the database.
     *
     * @param shortCode the short code to check
     * @return a Mono emitting {@code true} if the code exists
     */
    Mono<Boolean> existsByShortCode(String shortCode);
}
