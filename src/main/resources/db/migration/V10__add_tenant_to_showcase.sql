-- V10__add_tenant_to_showcase.sql
-- Add tenant_id to showcase_items to enforce multi-tenant isolation across salon galleries

ALTER TABLE showcase_items
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default' NOT NULL;

-- Backfill any existing records to the default salon tenant
UPDATE showcase_items
SET tenant_id = 'default'
WHERE tenant_id IS NULL;

-- Index on tenant_id for high-performance tenant gallery queries
CREATE INDEX IF NOT EXISTS idx_showcase_tenant_id ON showcase_items(tenant_id);
