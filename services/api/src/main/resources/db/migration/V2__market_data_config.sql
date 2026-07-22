CREATE TABLE market_data_config (
    id INTEGER PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    snapshot_source VARCHAR(30) NOT NULL,
    single_source VARCHAR(30) NOT NULL,
    refresh_seconds INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_market_data_singleton CHECK (id = 1),
    CONSTRAINT ck_market_data_refresh CHECK (refresh_seconds BETWEEN 5 AND 300)
);

INSERT INTO market_data_config (
    id, provider, mode, snapshot_source, single_source, refresh_seconds, updated_at
) VALUES (
    1, 'AKSHARE', 'MARKET_SNAPSHOT', 'EASTMONEY', 'EASTMONEY', 10, CURRENT_TIMESTAMP
);
