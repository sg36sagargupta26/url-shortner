/**
 * R2DBC entity classes mapped to PostgreSQL tables.
 *
 * <p>Each entity uses Spring Data R2DBC annotations ({@code @Table},
 * {@code @Id}, {@code @Column}) for reactive database mapping.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link com.shortly.model.Link} — shortened URL entity
 *       (table: {@code links})</li>
 *   <li>{@link com.shortly.model.Click} — individual click event
 *       (partitioned table: {@code clicks})</li>
 *   <li>{@link com.shortly.model.LinkStatsDaily} — daily aggregated
 *       statistics (table: {@code link_stats_daily})</li>
 * </ul>
 */
package com.shortly.model;
