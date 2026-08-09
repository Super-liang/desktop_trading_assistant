-- 0.1.6 多市场基础迁移：只增不删，旧 A 股记录必须可确定性回填。
ALTER TABLE securities ALTER COLUMN instrument_id TYPE VARCHAR(32);
ALTER TABLE securities ADD COLUMN market VARCHAR(20);
ALTER TABLE securities ADD COLUMN currency VARCHAR(3);
ALTER TABLE securities ADD COLUMN provider_symbol VARCHAR(32);

UPDATE securities
SET market = CASE WHEN exchange IN ('SSE', 'SZSE', 'BSE') THEN 'A_SHARE' END,
    currency = CASE WHEN exchange IN ('SSE', 'SZSE', 'BSE') THEN 'CNY' END,
    provider_symbol = code;

ALTER TABLE securities ALTER COLUMN market SET NOT NULL;
ALTER TABLE securities ALTER COLUMN currency SET NOT NULL;
ALTER TABLE securities ALTER COLUMN provider_symbol SET NOT NULL;
ALTER TABLE securities ADD CONSTRAINT ck_securities_market
    CHECK (market IN ('A_SHARE', 'HK_STOCK', 'US_STOCK', 'PUBLIC_FUND'));
ALTER TABLE securities ADD CONSTRAINT ck_securities_currency
    CHECK (currency IN ('CNY', 'HKD', 'USD'));
CREATE INDEX idx_securities_market_status_code ON securities(market, status, code);
CREATE INDEX idx_securities_market_name ON securities(market, name);

ALTER TABLE portfolio_items ADD COLUMN market VARCHAR(20);
ALTER TABLE portfolio_items ADD COLUMN currency VARCHAR(3);
ALTER TABLE portfolio_items ADD COLUMN opened_on DATE;

UPDATE portfolio_items
SET market = CASE WHEN exchange IN ('SSE', 'SZSE', 'BSE') THEN 'A_SHARE' END,
    currency = CASE WHEN exchange IN ('SSE', 'SZSE', 'BSE') THEN 'CNY' END,
    opened_on = CAST(created_at AT TIME ZONE 'Asia/Shanghai' AS DATE);

ALTER TABLE portfolio_items ALTER COLUMN market SET NOT NULL;
ALTER TABLE portfolio_items ALTER COLUMN currency SET NOT NULL;
ALTER TABLE portfolio_items ALTER COLUMN opened_on SET NOT NULL;
ALTER TABLE portfolio_items ADD CONSTRAINT ck_portfolio_market
    CHECK (market IN ('A_SHARE', 'HK_STOCK', 'US_STOCK', 'PUBLIC_FUND'));
ALTER TABLE portfolio_items ADD CONSTRAINT ck_portfolio_currency
    CHECK (currency IN ('CNY', 'HKD', 'USD'));
CREATE INDEX idx_portfolio_user_market_sort ON portfolio_items(user_id, market, sort_order);

ALTER TABLE user_operation_audits ADD COLUMN market VARCHAR(20);
ALTER TABLE user_operation_audits ADD COLUMN opened_on DATE;

CREATE TABLE market_sessions (
    market VARCHAR(20) NOT NULL,
    trading_date DATE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    open_at TIMESTAMP WITH TIME ZONE NOT NULL,
    break_start_at TIMESTAMP WITH TIME ZONE,
    break_end_at TIMESTAMP WITH TIME ZONE,
    close_at TIMESTAMP WITH TIME ZONE NOT NULL,
    early_close BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(60) NOT NULL,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (market, trading_date),
    CONSTRAINT ck_market_sessions_market CHECK (market IN ('A_SHARE', 'HK_STOCK', 'US_STOCK')),
    CONSTRAINT ck_market_sessions_break CHECK (
        (break_start_at IS NULL AND break_end_at IS NULL) OR
        (break_start_at IS NOT NULL AND break_end_at IS NOT NULL AND break_start_at < break_end_at)),
    CONSTRAINT ck_market_sessions_time CHECK (open_at < close_at)
);
CREATE INDEX idx_market_sessions_market_open ON market_sessions(market, open_at);

CREATE TABLE market_sync_runs (
    id UUID PRIMARY KEY,
    market VARCHAR(20) NOT NULL,
    trading_date DATE NOT NULL,
    job_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_type VARCHAR(80),
    CONSTRAINT uq_market_sync_run UNIQUE(market, trading_date, job_type),
    CONSTRAINT ck_market_sync_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE TABLE position_daily_baselines (
    position_id UUID NOT NULL REFERENCES portfolio_items(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    market VARCHAR(20) NOT NULL,
    trading_date DATE NOT NULL,
    opening_quantity NUMERIC(20,4),
    opening_price NUMERIC(20,6),
    currency VARCHAR(3) NOT NULL,
    quote_source VARCHAR(40),
    captured_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (position_id, trading_date),
    CONSTRAINT ck_position_baseline_status CHECK (status IN ('COMPLETE', 'UNAVAILABLE'))
);
CREATE INDEX idx_position_baselines_user_market_date
    ON position_daily_baselines(user_id, market, trading_date DESC);

CREATE TABLE fund_nav_quotes (
    instrument_id VARCHAR(32) NOT NULL REFERENCES securities(instrument_id),
    nav_date DATE NOT NULL,
    unit_nav NUMERIC(20,6) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (instrument_id, nav_date),
    CONSTRAINT ck_fund_nav_positive CHECK (unit_nav > 0)
);
