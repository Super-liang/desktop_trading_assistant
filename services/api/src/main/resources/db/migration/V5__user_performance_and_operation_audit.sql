CREATE TABLE user_operation_audits (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(40) NOT NULL,
    portfolio_item_id UUID,
    instrument_id VARCHAR(24),
    instrument_name VARCHAR(80),
    result VARCHAR(20) NOT NULL,
    request_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_operation_audit_action CHECK (action IN (
        'PORTFOLIO_CREATED', 'PORTFOLIO_UPDATED', 'PORTFOLIO_DELETED', 'PASSWORD_CHANGED')),
    CONSTRAINT ck_user_operation_audit_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX idx_user_operation_audits_user_created
    ON user_operation_audits(user_id, created_at DESC);
CREATE INDEX idx_user_operation_audits_user_action_created
    ON user_operation_audits(user_id, action, created_at DESC);

CREATE TABLE user_performance_daily (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trading_date DATE NOT NULL,
    daily_profit NUMERIC(24,4),
    daily_return_percent NUMERIC(16,6),
    year_profit NUMERIC(24,4),
    year_return_percent NUMERIC(16,6),
    annualized_return_percent NUMERIC(16,6),
    statistics_start_date DATE,
    status VARCHAR(20) NOT NULL,
    missing_quote_count INTEGER NOT NULL DEFAULT 0,
    quote_source VARCHAR(40) NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, trading_date),
    CONSTRAINT ck_user_performance_status CHECK (status IN (
        'COMPLETE', 'PARTIAL', 'UNAVAILABLE', 'ACCUMULATING')),
    CONSTRAINT ck_user_performance_missing_quotes CHECK (missing_quote_count >= 0)
);
CREATE INDEX idx_user_performance_daily_user_date
    ON user_performance_daily(user_id, trading_date DESC);
