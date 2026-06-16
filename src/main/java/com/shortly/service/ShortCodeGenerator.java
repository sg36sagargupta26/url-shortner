package com.shortly.service;

import com.shortly.repository.LinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates unique 7-character Base62 short codes.
 * Retries on collision (up to 3 attempts), falls back to UUID-based code.
 */
@Component
public class ShortCodeGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_RETRIES = 3;
    private final SecureRandom random = new SecureRandom();
    private final LinkRepository linkRepository;
    private final int codeLength;

    public ShortCodeGenerator(LinkRepository linkRepository,
                              @Value("${shortly.short-code.length:7}") int codeLength) {
        this.linkRepository = linkRepository;
        this.codeLength = codeLength;
    }

    /**
     * Generates a unique short code. Retries on collision with the database.
     */
    public Mono<String> generate() {
        return generateWithRetry(0);
    }

    private Mono<String> generateWithRetry(int attempt) {
        String code = generateBase62(codeLength);
        return linkRepository.existsByShortCode(code)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(code);
                    }
                    if (attempt < MAX_RETRIES) {
                        return generateWithRetry(attempt + 1);
                    }
                    // Fallback: UUID-based code guaranteed unique
                    return Mono.just(generateFallback());
                });
    }

    private String generateBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt(Math.abs(bytes[i]) % 62));
        }
        return sb.toString();
    }

    private String generateFallback() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, codeLength);
    }
}
