package com.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entity representing a single click event on a shortened link.
 *
 * <p>Mapped to the partitioned {@code clicks} table. Core analytics fields
 * (country, city) are denormalised columns; extended metadata (device,
 * browser, OS) is stored as a JSON string in the {@code metadata} column.
 */
@Table("clicks")
public class Click {

    @Id
    private Long id;

    @Column("link_id")
    private Long linkId;

    @Column("clicked_at")
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
    private String metadata = "{}";

    /** No-args constructor required by Spring Data R2DBC. */
    public Click() {}

    private Click(Builder builder) {
        this.linkId = builder.linkId;
        this.clickedAt = builder.clickedAt != null ? builder.clickedAt : Instant.now();
        this.ipHash = builder.ipHash;
        this.country = builder.country;
        this.city = builder.city;
        this.userAgent = builder.userAgent;
        this.referrer = builder.referrer;
        this.metadata = builder.metadata != null ? builder.metadata : "{}";
    }

    /** @return a new {@link Builder} instance */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link Click} instances fluently.
     */
    public static class Builder {
        private Long linkId;
        private Instant clickedAt;
        private String ipHash;
        private String country;
        private String city;
        private String userAgent;
        private String referrer;
        private String metadata;

        /** @param linkId the ID of the target link */
        public Builder linkId(Long linkId) { this.linkId = linkId; return this; }
        /** @param clickedAt the click timestamp (defaults to now) */
        public Builder clickedAt(Instant clickedAt) { this.clickedAt = clickedAt; return this; }
        /** @param ipHash the SHA-256 hash of the visitor's IP */
        public Builder ipHash(String ipHash) { this.ipHash = ipHash; return this; }
        /** @param country ISO 3166-1 alpha-2 country code */
        public Builder country(String country) { this.country = country; return this; }
        /** @param city city name */
        public Builder city(String city) { this.city = city; return this; }
        /** @param userAgent the raw User-Agent header */
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        /** @param referrer the raw Referer header */
        public Builder referrer(String referrer) { this.referrer = referrer; return this; }
        /** @param metadata JSON string with device, browser, and OS */
        public Builder metadata(String metadata) { this.metadata = metadata; return this; }

        /** @return a new {@link Click} from this builder's state */
        public Click build() { return new Click(this); }
    }

    // ── Getters / Setters ──

    /** @return the auto-generated primary key */
    public Long getId() { return id; }
    /** @param id the primary key */
    public void setId(Long id) { this.id = id; }

    /** @return the ID of the associated link */
    public Long getLinkId() { return linkId; }
    /** @param linkId the associated link ID */
    public void setLinkId(Long linkId) { this.linkId = linkId; }

    /** @return the click timestamp */
    public Instant getClickedAt() { return clickedAt; }
    /** @param clickedAt the click timestamp */
    public void setClickedAt(Instant clickedAt) { this.clickedAt = clickedAt; }

    /** @return the hashed visitor IP */
    public String getIpHash() { return ipHash; }
    /** @param ipHash the hashed visitor IP */
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    /** @return the ISO country code */
    public String getCountry() { return country; }
    /** @param country the ISO country code */
    public void setCountry(String country) { this.country = country; }

    /** @return the city name */
    public String getCity() { return city; }
    /** @param city the city name */
    public void setCity(String city) { this.city = city; }

    /** @return the raw User-Agent string */
    public String getUserAgent() { return userAgent; }
    /** @param userAgent the raw User-Agent string */
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    /** @return the raw Referer header */
    public String getReferrer() { return referrer; }
    /** @param referrer the raw Referer header */
    public void setReferrer(String referrer) { this.referrer = referrer; }

    /** @return the JSON metadata string */
    public String getMetadata() { return metadata; }
    /** @param metadata the JSON metadata string */
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
