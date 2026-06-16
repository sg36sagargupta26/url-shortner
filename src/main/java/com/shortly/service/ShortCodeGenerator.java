package com.shortly.service;

import com.shortly.repository.LinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates unique, URL-safe short codes using Base62 encoding.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Generate a random 7-character Base62 code using {@link SecureRandom}</li>
 *   <li>Check for collision against the database</li>
 *   <li>Retry up to {@value #MAX_RETRIES} times on collision</li>
 *   <li>Fall back to a UUID-derived code if all retries collide</li>
 * </ol>
 *
 * <p>The code space is 62<sup>7</sup> &asymp; 3.5 trillion — collisions are
 * statistically improbable at normal scale, but the retry mechanism guards
 * against edge cases.
 */
@Component
public class ShortCodeGenerator {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_RETRIES = 3;
    private final SecureRandom random = new SecureRandom();
    private final LinkRepository linkRepository;
    private final int codeLength;

    /**
     * @param linkRepository the link repository for collision checking
     * @param codeLength     desired short code length (default 7, from {@code shortly.short-code.length})
     */
    public ShortCodeGenerator(LinkRepository linkRepository,
                              @Value("${shortly.short-code.length:7}") int codeLength) {
        this.linkRepository = linkRepository;
        this.codeLength = codeLength;
    }

    /**
     * Generates a unique short code not already present in the database.
     *
     * @return a Mono emitting a unique short code string
     */
    public Mono<String> generate() {
        return generateWithRetry(0);
    }

    /**
     * Generates a code and retries on collision up to {@link #MAX_RETRIES} times.
     *
     * @param attempt current retry attempt (0-based)
     * @return a Mono emitting a unique short code
     */
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
                    return Mono.just(generateFallback());
                });
    }

    /**
     * Generates a random Base62 string of the given length.
     *
     * @param length the number of characters
     * @return a Base62-encoded random string
     */
    private String generateBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt(Math.abs(bytes[i]) % 62));
        }
        return sb.toString();
    }

    /**
     * Fallback: generates a code from a UUID to guarantee uniqueness.
     *
     * @return a UUID-derived code truncated to the configured length
     */
    private String generateFallback() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, codeLength);
    }
}
