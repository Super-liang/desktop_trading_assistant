ALTER TABLE position_daily_baselines ADD COLUMN status_reason VARCHAR(30);
ALTER TABLE position_daily_baselines ADD CONSTRAINT ck_position_baseline_reason
    CHECK (status_reason IS NULL OR status_reason IN ('MISSING_OPEN', 'MUTATED_AFTER_OPEN'));
