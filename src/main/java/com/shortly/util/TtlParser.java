package com.shortly.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable TTL strings like "30d", "24h", "60m", "3600s".
 * Also accepts raw seconds as a number string.
 */
public class TtlParser {

    private static final Pattern TTL_PATTERN =
            Pattern.compile("^(\\d+)\\s*(d|h|m|s|day|days|hour|hours|min|mins|minute|minutes|sec|secs|second|seconds)$",
                    Pattern.CASE_INSENSITIVE);

    private static final long MAX_TTL_SECONDS = 365L * 24 * 60 * 60; // 365 days
    private static final long DEFAULT_TTL_SECONDS = 30L * 24 * 60 * 60; // 30 days

    /**
     * Parses a TTL string. Returns Duration if valid, throws IllegalArgumentException if not.
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
