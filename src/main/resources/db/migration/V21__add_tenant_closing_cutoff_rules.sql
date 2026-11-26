-- Configurable per-tenant late-day closing grace window and last-intake cutoff rules.
-- closing_grace_minutes: extra minutes past official closing time allowed to finish a service (default 30 -> 7:30 PM hard finish for 7:00 PM close).
-- last_intake_before_close_minutes: minutes before official closing time after which no new appointments can start (default 30 -> 6:30 PM latest start for 7:00 PM close).
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS closing_grace_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS last_intake_before_close_minutes INT NOT NULL DEFAULT 30;
