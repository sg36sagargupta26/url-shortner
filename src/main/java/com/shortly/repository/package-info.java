/**
 * Reactive Spring Data R2DBC repository interfaces.
 *
 * <p>Each repository extends {@code ReactiveCrudRepository} for non-blocking
 * database access. Custom query methods use {@code @Query} annotations with
 * native SQL where Spring Data query derivation is insufficient.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link com.shortly.repository.LinkRepository} — CRUD for {@code links}</li>
 *   <li>{@link com.shortly.repository.ClickRepository} — CRUD for {@code clicks}
 *       including retention-policy deletes</li>
 *   <li>{@link com.shortly.repository.LinkStatsDailyRepository} — CRUD for
 *       {@code link_stats_daily} with date-range queries</li>
 * </ul>
 */
package com.shortly.repository;
