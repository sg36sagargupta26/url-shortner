package com.shortly.repository;

import com.shortly.model.Link;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface LinkRepository extends ReactiveCrudRepository<Link, Long> {

    Mono<Link> findByShortCode(String shortCode);

    Mono<Boolean> existsByShortCode(String shortCode);
}
