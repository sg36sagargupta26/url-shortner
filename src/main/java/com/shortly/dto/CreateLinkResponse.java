package com.shortly.dto;

import java.time.Instant;

/**
 * Response body returned after successfully creating a shortened URL.
 *
 * @param shortCode    the generated short code
 * @param shortUrl     the full shortened URL
 * @param originalUrl  the original long URL
 * @param expiresAt    expiry timestamp in ISO-8601 format
 * @param redirectType the redirect type used ({@code "301"} or {@code "302"})
 * @param createdAt    creation timestamp in ISO-8601 format
 */
public record CreateLinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant expiresAt,
        String redirectType,
        Instant createdAt
) {

    /** Base URL used to construct the full short URL. */
    private static final String BASE_URL = "https://short.ly/";

    /**
     * Factory method that builds a response from a generated short code and request data.
     *
     * @param shortCode    the generated short code
     * @param originalUrl  the original long URL
     * @param expiresAt    expiry timestamp
     * @param redirectType redirect type
     * @return a fully populated response record
     */
    public static CreateLinkResponse of(String shortCode, String originalUrl,
                                         Instant expiresAt, String redirectType) {
        return new CreateLinkResponse(
                shortCode,
                BASE_URL + shortCode,
                originalUrl,
                expiresAt,
                redirectType,
                Instant.now()
        );
    }
}
