-- Drop old partitioned clicks table and recreate as simple table
-- Partitioning is overkill for MVP; 30-day retention keeps rows manageable

DROP TABLE IF EXISTS clicks CASCADE;

CREATE TABLE clicks (
    id          BIGSERIAL       PRIMARY KEY,
    link_id     BIGINT          NOT NULL REFERENCES links(id),
    clicked_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ip_hash     VARCHAR(64),
    country     VARCHAR(2),
    city        VARCHAR(100),
    user_agent  TEXT,
    referrer    TEXT,
    metadata    JSONB           DEFAULT '{}'
);

CREATE INDEX idx_clicks_link_id_clicked_at ON clicks (link_id, clicked_at DESC);
CREATE INDEX idx_clicks_clicked_at ON clicks (clicked_at DESC);
