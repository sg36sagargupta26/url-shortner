/**
 * Shared utility classes with no external dependencies beyond the JDK.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link com.shortly.util.TtlParser} — parses human-readable TTL
 *       strings ({@code "30d"}, {@code "3600"}) into {@code Duration}</li>
 *   <li>{@link com.shortly.util.IpHashUtil} — SHA-256 hashing of IP
 *       addresses for privacy-safe storage and unique counting</li>
 * </ul>
 */
package com.shortly.util;
