package com.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("clicks")
public class Click {

    @Id
    private Long id;

    @Column("link_id")
    private Long linkId;

    @Column("clicked_at")
    @Builder.Default
    private Instant clickedAt = Instant.now();

    @Column("ip_hash")
    private String ipHash;

    @Column("country")
    private String country;

    @Column("city")
    private String city;

    @Column("user_agent")
    private String userAgent;

    @Column("referrer")
    private String referrer;

    @Column("metadata")
    @Builder.Default
    private String metadata = "{}"; // JSON string for device, browser, os
}
