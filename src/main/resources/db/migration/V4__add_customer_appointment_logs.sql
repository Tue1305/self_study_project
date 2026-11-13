-- V4 Customer Appointment History & Audit Log System (PostgreSQL)

-- 1. Create appointment_activity_logs table
CREATE TABLE IF NOT EXISTS appointment_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    customer_id BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    appointment_id BIGINT REFERENCES appointments(id) ON DELETE CASCADE,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(255),
    action_type VARCHAR(64) NOT NULL,
    performed_by VARCHAR(255) DEFAULT 'SYSTEM',
    notes TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Indexes for tenant-scoped query performance and audit reporting
CREATE INDEX IF NOT EXISTS idx_appointment_logs_tenant_id ON appointment_activity_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_appointment_logs_customer_id ON appointment_activity_logs (customer_id);
CREATE INDEX IF NOT EXISTS idx_appointment_logs_appointment_id ON appointment_activity_logs (appointment_id);
CREATE INDEX IF NOT EXISTS idx_appointment_logs_customer_email ON appointment_activity_logs (customer_email);
CREATE INDEX IF NOT EXISTS idx_appointment_logs_created_at ON appointment_activity_logs (created_at);

-- 3. Ensure customer table has tenant_id column for multi-tenant customer tracking
ALTER TABLE customer ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_customer_tenant_id ON customer (tenant_id);
