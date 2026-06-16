/**
 * Root package for the Shortly URL Shortener application.
 *
 * <p>Contains the {@link com.shortly.ShortlyApplication} entry point
 * and enables Spring Boot auto-configuration with component scanning
 * across all sub-packages.
 *
 * <p><strong>Package structure:</strong>
 * <ul>
 *   <li>{@code config} — Spring configuration beans (Redis, CORS)</li>
 *   <li>{@code controller} — REST controllers and global error handling</li>
 *   <li>{@code dto} — data transfer objects (request/response records)</li>
 *   <li>{@code model} — R2DBC entity classes mapped to PostgreSQL tables</li>
 *   <li>{@code repository} — reactive Spring Data R2DBC repositories</li>
 *   <li>{@code service} — business logic, analytics, caching, and scheduled jobs</li>
 *   <li>{@code util} — shared utilities (TTL parser, IP hashing)</li>
 * </ul>
 */
package com.shortly;
