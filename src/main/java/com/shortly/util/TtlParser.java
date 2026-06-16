package com.shortly.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable TTL (time-to-live) strings into {@link Duration} objects.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>Raw seconds: {@code "3600"}</li>
 *   <li>Human-readable: {@code "30d"}, {@code "24h"}, {@code "60m"}, {@code "90s"}</li>
 *   <li>Long-form: {@code "30 days"}, {@code "24 hours"}, {@code "60 minutes"}, {@code "90 seconds"}</li>
 * </ul>
 *
 * <p>Constraints:
 * <ul>
 *   <li>TTL must be positive</li>
 *   <li>Maximum TTL is 365 days</li>
 *   <li>Default (when null/blank) is 30 days</li>
 * </ul>
 */
public class TtlParser {

    private static final Pattern TTL_PATTERN =
            Pattern.compile("^(\\d+)\\s*(d|h|m|s|day|days|hour|hours|min|mins|minute|minutes|sec|secs|second|seconds)$",
                    Pattern.CASE_INSENSITIVE);

    /** Maximum allowed TTL in seconds (365 days). */
    private static final long MAX_TTL_SECONDS = 365L * 24 * 60 * 60;

    /** Default TTL in seconds when none is specified (30 days). */
    private static final long DEFAULT_TTL_SECONDS = 30L * 24 * 60 * 60;

    /**
     * Parses a TTL string into a {@link Duration}.
     *
     * @param ttl the TTL string (e.g. {@code "30d"}, {@code "3600"}, or null for default)
     * @return the parsed duration
     * @throws IllegalArgumentException if the format is invalid or exceeds the maximum
     */
    public static Duration parse(String ttl) {
        if (ttl == null || ttl.isBlank()) {
            return Duration.ofSeconds(DEFAULT_TTL_SECONDS);
        }

        // Try raw seconds
        try {
            long seconds = Long.parseLong(ttl.trim());
            return validateAndCreate(seconds);
        } catch (NumberFormatException ignored) {
            // Not a raw number, try human-readable
        }

        Matcher matcher = TTL_PATTERN.matcher(ttl.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid TTL format: '" + ttl + "'. Use formats like 30d, 24h, 60m, 3600s.");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        long seconds = switch (unit) {
            case "s", "sec", "secs", "second", "seconds" -> value;
            case "m", "min", "mins", "minute", "minutes" -> value * 60;
            case "h", "hour", "hours" -> value * 3600;
            case "d", "day", "days" -> value * 86400;
            default -> throw new IllegalArgumentException("Unknown TTL unit: " + unit);
        };

        return validateAndCreate(seconds);
    }

    /**
     * Validates the given number of seconds and creates a Duration.
     *
     * @param seconds the TTL in seconds
     * @return the validated Duration
     * @throws IllegalArgumentException if seconds is non-positive or exceeds the maximum
     */
    private static Duration validateAndCreate(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("TTL must be positive, got: " + seconds + "s");
        }
        if (seconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "TTL exceeds maximum of 365 days (" + MAX_TTL_SECONDS + "s), got: " + seconds + "s");
        }
        return Duration.ofSeconds(seconds);
    }
}
