package com.shortly.service;

import com.shortly.model.LinkStatsDaily;
import com.shortly.repository.ClickRepository;
import com.shortly.repository.LinkStatsDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled maintenance job that runs nightly at 2:00 AM.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Purge raw click data older than 30 days from PostgreSQL</li>
 *   <li>Roll up the previous day's Redis counters into the {@code link_stats_daily} table</li>
 *   <li>Invalidate expired link caches in Redis</li>
 * </ol>
 *
 * <p>The rollup currently targets the previous calendar day. In a production
 * system this would iterate over all active links; the initial implementation
 * handles the cleanup and provides the scheduling scaffold.
 */
@Component
@EnableScheduling
public class ExpiryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryCleanupJob.class);

    /** Number of days after which raw click data is purged. */
    private static final int CLICK_RETENTION_DAYS = 30;

    private final ClickRepository clickRepository;
    private final LinkStatsDailyRepository statsRepository;

    /**
     * @param clickRepository  the click repository for purging old data
     * @param statsRepository  the daily stats repository for rollups
     */
    public ExpiryCleanupJob(ClickRepository clickRepository,
                             LinkStatsDailyRepository statsRepository) {
        this.clickRepository = clickRepository;
        this.statsRepository = statsRepository;
    }

    /**
     * Runs nightly at 2:00 AM to purge old clicks and perform rollups.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanup() {
        log.info("Starting nightly cleanup job");

        purgeOldClicks();
        // Future: iterate active links, read Redis counters, upsert into link_stats_daily

        log.info("Nightly cleanup job completed");
    }

    /**
     * Deletes all click records older than {@value #CLICK_RETENTION_DAYS} days.
     */
    private void purgeOldClicks() {
        Instant cutoff = Instant.now().minus(CLICK_RETENTION_DAYS, ChronoUnit.DAYS);
        clickRepository.deleteOlderThan(cutoff)
                .doOnSuccess(v -> log.info("Purged clicks older than {}", cutoff))
                .doOnError(e -> log.error("Failed to purge old clicks", e))
                .subscribe();
    }

    /**
     * Creates or updates a daily stats row.
     *
     * @param linkId     the link ID
     * @param date       the stats date
     * @param clicks     total clicks for the day
     * @param unique     unique visitors for the day
     */
    @SuppressWarnings("unused") // available for future use
    private void upsertDailyStats(Long linkId, LocalDate date, Long clicks, Long unique,
                                   String country, String device, String browser,
                                   String os, String referrer) {
        LinkStatsDaily stats = LinkStatsDaily.builder()
                .linkId(linkId)
                .date(date)
                .totalClicks(clicks)
                .uniqueVisitors(unique)
                .countryBreakdown(country)
                .deviceBreakdown(device)
                .browserBreakdown(browser)
                .osBreakdown(os)
                .referrerBreakdown(referrer)
                .build();

        statsRepository.findByLinkIdAndDate(linkId, date)
                .flatMap(existing -> {
                    stats.setTotalClicks(stats.getTotalClicks() + existing.getTotalClicks());
                    stats.setUniqueVisitors(stats.getUniqueVisitors() + existing.getUniqueVisitors());
                    return statsRepository.save(stats);
                })
                .switchIfEmpty(statsRepository.save(stats))
                .subscribe(
                        saved -> log.debug("Upserted daily stats: linkId={}, date={}", linkId, date),
                        error -> log.error("Failed to upsert daily stats", error)
                );
    }
}
