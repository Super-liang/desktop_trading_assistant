CREATE TABLE securities (
    instrument_id VARCHAR(16) PRIMARY KEY,
    exchange VARCHAR(10) NOT NULL,
    code VARCHAR(12) NOT NULL,
    name VARCHAR(80) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_securities_exchange_code UNIQUE(exchange, code)
);

CREATE INDEX idx_securities_code ON securities(code);
CREATE INDEX idx_securities_status ON securities(status);
CREATE INDEX idx_securities_name ON securities(name);
