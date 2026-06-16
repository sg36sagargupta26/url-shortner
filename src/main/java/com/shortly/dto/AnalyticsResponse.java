package com.shortly.dto;

import java.util.List;
import java.util.Map;

/**
 * Response body for the analytics endpoint.
 *
 * @param shortCode       the link's short code
 * @param totalClicks     total click count (real-time from Redis)
 * @param uniqueVisitors  estimated unique visitors (HyperLogLog from Redis)
 * @param byCountry       country breakdown as key-value pairs
 * @param byDevice        device type breakdown
 * @param byBrowser       browser breakdown
 * @param byOs            operating system breakdown
 * @param byReferrer      referrer URL breakdown
 * @param dailyBreakdown  daily click counts (from Postgres rollups)
 */
public record AnalyticsResponse(
        String shortCode,
        long totalClicks,
        long uniqueVisitors,
        List<CategoryCount> byCountry,
        List<CategoryCount> byDevice,
        List<CategoryCount> byBrowser,
        List<CategoryCount> byOs,
        List<CategoryCount> byReferrer,
        List<DailyBreakdown> dailyBreakdown
) {

    /**
     * A single category count entry.
     *
     * @param key   the category name (e.g. {@code "US"} or {@code "Chrome"})
     * @param count the number of clicks in this category
     */
    public record CategoryCount(String key, long count) {}

    /**
     * A single day's click counts.
     *
     * @param date   the ISO date string (e.g. {@code "2026-06-16"})
     * @param clicks total clicks for the day
     * @param unique estimated unique visitors for the day
     */
    public record DailyBreakdown(String date, long clicks, long unique) {}
}
