CREATE TABLE link_stats_daily (
    link_id             BIGINT  NOT NULL REFERENCES links(id),
    date                DATE    NOT NULL,
    total_clicks        BIGINT  NOT NULL DEFAULT 0,
    unique_visitors     BIGINT  NOT NULL DEFAULT 0,
    country_breakdown   JSONB   DEFAULT '[]',
    device_breakdown    JSONB   DEFAULT '[]',
    browser_breakdown   JSONB   DEFAULT '[]',
    os_breakdown        JSONB   DEFAULT '[]',
    referrer_breakdown  JSONB   DEFAULT '[]',

    PRIMARY KEY (link_id, date)
);

CREATE INDEX idx_link_stats_daily_date ON link_stats_daily (date);
