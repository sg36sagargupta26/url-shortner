package com.shortly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a shortened URL.
 *
 * @param url           the long URL to shorten (required, must be valid HTTP/HTTPS)
 * @param ttl           time-to-live string (e.g. {@code "30d"}, {@code "3600"}; default 30d)
 * @param redirectType  redirect type: {@code "301"} or {@code "302"} (default 302)
 */
public record CreateLinkRequest(

        @NotBlank(message = "URL is required")
        @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        String url,

        @Size(max = 50, message = "TTL must not exceed 50 characters")
        String ttl,

        @Pattern(regexp = "301|302", message = "redirectType must be '301' or '302'")
        String redirectType
) {}
