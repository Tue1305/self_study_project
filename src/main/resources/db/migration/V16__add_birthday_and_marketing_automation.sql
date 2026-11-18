-- Migration V16: Add Birthday & Automated Marketing Retention tracking to customers

ALTER TABLE customer ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_birthday_perk_year INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_retention_reminder_sent_at TIMESTAMP;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS opt_out_marketing BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_customer_birth_date ON customer (tenant_id, birth_date);
CREATE INDEX IF NOT EXISTS idx_customer_retention ON customer (tenant_id, last_retention_reminder_sent_at);
