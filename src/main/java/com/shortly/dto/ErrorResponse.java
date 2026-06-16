package com.shortly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standardised error response for all API error scenarios.
 *
 * @param error            a machine-readable error code (e.g. {@code "link_expired"})
 * @param message          a human-readable description
 * @param retryAfterSeconds suggested seconds to wait before retrying (only for 429)
 * @param expiredAt         the expiry timestamp (only for 410 expired links)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Integer retryAfterSeconds,
        Instant expiredAt
) {

    /** Creates a generic error response. */
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null, null);
    }

    /** Creates a rate-limit error with retry-after header. */
    public static ErrorResponse rateLimited(String message, int retryAfterSeconds) {
        return new ErrorResponse("rate_limited", message, retryAfterSeconds, null);
    }

    /** Creates an expired-link error. */
    public static ErrorResponse expired(String message, Instant expiredAt) {
        return new ErrorResponse("link_expired", message, null, expiredAt);
    }

    /** Creates a not-found error. */
    public static ErrorResponse notFound(String message) {
        return new ErrorResponse("not_found", message, null, null);
    }

    /** Creates a validation error. */
    public static ErrorResponse validationError(String message) {
        return new ErrorResponse("validation_error", message, null, null);
    }
}
