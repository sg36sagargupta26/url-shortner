CREATE TABLE links (
    id            BIGSERIAL       PRIMARY KEY,
    short_code    VARCHAR(10)     NOT NULL UNIQUE,
    original_url  TEXT            NOT NULL,
    redirect_type VARCHAR(3)      NOT NULL DEFAULT '302'
                                  CHECK (redirect_type IN ('301', '302')),
    expires_at    TIMESTAMPTZ     NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    is_active     BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX idx_links_short_code ON links (short_code);
CREATE INDEX idx_links_expires_at ON links (expires_at);
CREATE INDEX idx_links_created_at ON links (created_at);
