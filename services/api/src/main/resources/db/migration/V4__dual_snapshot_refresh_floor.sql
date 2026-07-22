-- 双源并行后新浪接口成为固定参与者，统一保护最低 30 秒刷新间隔。
UPDATE market_data_config
SET refresh_seconds = 30,
    updated_at = CURRENT_TIMESTAMP
WHERE refresh_seconds < 30;

ALTER TABLE market_data_config DROP CONSTRAINT ck_market_data_refresh;
ALTER TABLE market_data_config ADD CONSTRAINT ck_market_data_refresh
    CHECK (refresh_seconds BETWEEN 30 AND 300);
