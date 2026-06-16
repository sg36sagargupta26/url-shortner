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
@Table("links")
public class Link {

    @Id
    private Long id;

    @Column("short_code")
    private String shortCode;

    @Column("original_url")
    private String originalUrl;

    @Column("redirect_type")
    @Builder.Default
    private String redirectType = "302";

    @Column("expires_at")
    private Instant expiresAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;
}
