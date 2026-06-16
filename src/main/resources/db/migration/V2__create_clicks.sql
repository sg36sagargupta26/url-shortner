CREATE TABLE clicks (
    id          BIGSERIAL       NOT NULL,
    link_id     BIGINT          NOT NULL REFERENCES links(id),
    clicked_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ip_hash     VARCHAR(64),
    country     VARCHAR(2),
    city        VARCHAR(100),
    user_agent  TEXT,
    referrer    TEXT,
    metadata    JSONB           DEFAULT '{}',
    PRIMARY KEY (id, clicked_at)
) PARTITION BY RANGE (clicked_at);

-- Create partitions for 2026 months (and current)
CREATE TABLE clicks_2026_06 PARTITION OF clicks
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE clicks_2026_07 PARTITION OF clicks
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE clicks_2026_08 PARTITION OF clicks
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE clicks_2026_09 PARTITION OF clicks
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE clicks_2026_10 PARTITION OF clicks
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE clicks_2026_11 PARTITION OF clicks
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE clicks_2026_12 PARTITION OF clicks
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE clicks_2027_01 PARTITION OF clicks
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');

CREATE INDEX idx_clicks_link_id_clicked_at ON clicks (link_id, clicked_at DESC);
CREATE INDEX idx_clicks_clicked_at ON clicks (clicked_at DESC);
