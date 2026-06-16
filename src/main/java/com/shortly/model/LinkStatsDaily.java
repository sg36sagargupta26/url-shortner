package com.shortly.model;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("link_stats_daily")
public class LinkStatsDaily {

    @Column("link_id")
    private Long linkId;

    @Column("date")
    private LocalDate date;

    @Column("total_clicks")
    private Long totalClicks = 0L;

    @Column("unique_visitors")
    private Long uniqueVisitors = 0L;

    @Column("country_breakdown")
    private String countryBreakdown = "[]";

    @Column("device_breakdown")
    private String deviceBreakdown = "[]";

    @Column("browser_breakdown")
    private String browserBreakdown = "[]";

    @Column("os_breakdown")
    private String osBreakdown = "[]";

    @Column("referrer_breakdown")
    private String referrerBreakdown = "[]";

    public LinkStatsDaily() {}

    private LinkStatsDaily(Builder builder) {
        this.linkId = builder.linkId;
        this.date = builder.date;
        this.totalClicks = builder.totalClicks != null ? builder.totalClicks : 0L;
        this.uniqueVisitors = builder.uniqueVisitors != null ? builder.uniqueVisitors : 0L;
        this.countryBreakdown = builder.countryBreakdown != null ? builder.countryBreakdown : "[]";
        this.deviceBreakdown = builder.deviceBreakdown != null ? builder.deviceBreakdown : "[]";
        this.browserBreakdown = builder.browserBreakdown != null ? builder.browserBreakdown : "[]";
        this.osBreakdown = builder.osBreakdown != null ? builder.osBreakdown : "[]";
        this.referrerBreakdown = builder.referrerBreakdown != null ? builder.referrerBreakdown : "[]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long linkId;
        private LocalDate date;
        private Long totalClicks;
        private Long uniqueVisitors;
        private String countryBreakdown;
        private String deviceBreakdown;
        private String browserBreakdown;
        private String osBreakdown;
        private String referrerBreakdown;

        public Builder linkId(Long linkId) { this.linkId = linkId; return this; }
        public Builder date(LocalDate date) { this.date = date; return this; }
        public Builder totalClicks(Long totalClicks) { this.totalClicks = totalClicks; return this; }
        public Builder uniqueVisitors(Long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; return this; }
        public Builder countryBreakdown(String countryBreakdown) { this.countryBreakdown = countryBreakdown; return this; }
        public Builder deviceBreakdown(String deviceBreakdown) { this.deviceBreakdown = deviceBreakdown; return this; }
        public Builder browserBreakdown(String browserBreakdown) { this.browserBreakdown = browserBreakdown; return this; }
        public Builder osBreakdown(String osBreakdown) { this.osBreakdown = osBreakdown; return this; }
        public Builder referrerBreakdown(String referrerBreakdown) { this.referrerBreakdown = referrerBreakdown; return this; }

        public LinkStatsDaily build() {
            return new LinkStatsDaily(this);
        }
    }

    // ── Getters / Setters ──

    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getTotalClicks() { return totalClicks; }
    public void setTotalClicks(Long totalClicks) { this.totalClicks = totalClicks; }

    public Long getUniqueVisitors() { return uniqueVisitors; }
    public void setUniqueVisitors(Long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }

    public String getCountryBreakdown() { return countryBreakdown; }
    public void setCountryBreakdown(String countryBreakdown) { this.countryBreakdown = countryBreakdown; }

    public String getDeviceBreakdown() { return deviceBreakdown; }
    public void setDeviceBreakdown(String deviceBreakdown) { this.deviceBreakdown = deviceBreakdown; }

    public String getBrowserBreakdown() { return browserBreakdown; }
    public void setBrowserBreakdown(String browserBreakdown) { this.browserBreakdown = browserBreakdown; }

    public String getOsBreakdown() { return osBreakdown; }
    public void setOsBreakdown(String osBreakdown) { this.osBreakdown = osBreakdown; }

    public String getReferrerBreakdown() { return referrerBreakdown; }
    public void setReferrerBreakdown(String referrerBreakdown) { this.referrerBreakdown = referrerBreakdown; }
}
