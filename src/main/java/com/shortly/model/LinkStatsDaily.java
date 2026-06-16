package com.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("link_stats_daily")
public class LinkStatsDaily {

    @Column("link_id")
    private Long linkId;

    @Column("date")
    private LocalDate date;

    @Column("total_clicks")
    @Builder.Default
    private Long totalClicks = 0L;

    @Column("unique_visitors")
    @Builder.Default
    private Long uniqueVisitors = 0L;

    @Column("country_breakdown")
    @Builder.Default
    private String countryBreakdown = "[]";

    @Column("device_breakdown")
    @Builder.Default
    private String deviceBreakdown = "[]";

    @Column("browser_breakdown")
    @Builder.Default
    private String browserBreakdown = "[]";

    @Column("os_breakdown")
    @Builder.Default
    private String osBreakdown = "[]";

    @Column("referrer_breakdown")
    @Builder.Default
    private String referrerBreakdown = "[]";
}
