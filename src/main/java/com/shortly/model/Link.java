package com.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("links")
public class Link {

    @Id
    private Long id;

    @Column("short_code")
    private String shortCode;

    @Column("original_url")
    private String originalUrl;

    @Column("redirect_type")
    private String redirectType = "302";

    @Column("expires_at")
    private Instant expiresAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("is_active")
    private Boolean isActive = true;

    public Link() {}

    private Link(Builder builder) {
        this.shortCode = builder.shortCode;
        this.originalUrl = builder.originalUrl;
        this.redirectType = builder.redirectType != null ? builder.redirectType : "302";
        this.expiresAt = builder.expiresAt;
        this.createdAt = builder.createdAt;
        this.isActive = builder.isActive != null ? builder.isActive : true;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Builder ──

    public static class Builder {
        private String shortCode;
        private String originalUrl;
        private String redirectType;
        private Instant expiresAt;
        private Instant createdAt;
        private Boolean isActive;

        public Builder shortCode(String shortCode) { this.shortCode = shortCode; return this; }
        public Builder originalUrl(String originalUrl) { this.originalUrl = originalUrl; return this; }
        public Builder redirectType(String redirectType) { this.redirectType = redirectType; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder isActive(Boolean isActive) { this.isActive = isActive; return this; }

        public Link build() {
            return new Link(this);
        }
    }

    // ── Getters / Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public String getRedirectType() { return redirectType; }
    public void setRedirectType(String redirectType) { this.redirectType = redirectType; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    @Override
    public String toString() {
        return "Link{id=" + id + ", shortCode='" + shortCode + '\''
                + ", originalUrl='" + originalUrl + '\'' + ", redirectType='" + redirectType + '\''
                + ", expiresAt=" + expiresAt + ", isActive=" + isActive + '}';
    }
}
