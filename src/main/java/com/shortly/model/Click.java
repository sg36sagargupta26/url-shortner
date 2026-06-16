package com.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long linkId;
        private Instant clickedAt;
        private String ipHash;
        private String country;
        private String city;
        private String userAgent;
        private String referrer;
        private String metadata;

        public Builder linkId(Long linkId) { this.linkId = linkId; return this; }
        public Builder clickedAt(Instant clickedAt) { this.clickedAt = clickedAt; return this; }
        public Builder ipHash(String ipHash) { this.ipHash = ipHash; return this; }
        public Builder country(String country) { this.country = country; return this; }
        public Builder city(String city) { this.city = city; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder referrer(String referrer) { this.referrer = referrer; return this; }
        public Builder metadata(String metadata) { this.metadata = metadata; return this; }

        public Click build() {
            return new Click(this);
        }
    }

    // ── Getters / Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }

    public Instant getClickedAt() { return clickedAt; }
    public void setClickedAt(Instant clickedAt) { this.clickedAt = clickedAt; }

    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
