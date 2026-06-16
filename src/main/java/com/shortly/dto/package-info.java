/**
 * Data transfer objects (DTOs) for API request and response payloads.
 *
 * <p>All DTOs are Java {@code record} types for immutability and conciseness.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link com.shortly.dto.CreateLinkRequest} — validated request body
 *       for creating a short URL</li>
 *   <li>{@link com.shortly.dto.CreateLinkResponse} — response body after
 *       successful link creation</li>
 *   <li>{@link com.shortly.dto.AnalyticsResponse} — full analytics payload
 *       with nested category counts and daily breakdowns</li>
 *   <li>{@link com.shortly.dto.ErrorResponse} — standardised error body
 *       with static factory methods for common error types</li>
 * </ul>
 */
package com.shortly.dto;
