/**
 * REST controllers and global exception handling.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link com.shortly.controller.LinkController} — POST /api/v1/links
 *       (create short URL) and GET /api/v1/links/{code}/analytics</li>
 *   <li>{@link com.shortly.controller.RedirectController} — GET /{shortCode}
 *       (redirect with click recording)</li>
 *   <li>{@link com.shortly.controller.HealthController} — GET /health
 *       (Redis and DB health probes)</li>
 *   <li>{@link com.shortly.controller.GlobalErrorHandler} — translates
 *       exceptions into structured {@code ErrorResponse} JSON</li>
 * </ul>
 */
package com.shortly.controller;
