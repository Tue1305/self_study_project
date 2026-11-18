-- V12__add_pos_financial_transactions.sql
-- Add POS financial transactions table for checkout, tips, gratuity tracking, add-ons, sales taxes, and split payments

CREATE TABLE IF NOT EXISTS appointment_transactions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) DEFAULT 'default' NOT NULL,
    appointment_id BIGINT NOT NULL,
    customer_id BIGINT,
    nail_tech_id BIGINT,
    nail_tech_name VARCHAR(255),
    customer_name VARCHAR(255),
    subtotal NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0825,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    tip_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    tip_percent NUMERIC(5, 2),
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(64) NOT NULL DEFAULT 'CARD',
    split_details VARCHAR(1000),
    line_items_json TEXT,
    notes VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_appt_trans_tenant ON appointment_transactions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_appt_trans_appt ON appointment_transactions(tenant_id, appointment_id);
CREATE INDEX IF NOT EXISTS idx_appt_trans_tech ON appointment_transactions(tenant_id, nail_tech_id);
CREATE INDEX IF NOT EXISTS idx_appt_trans_created ON appointment_transactions(tenant_id, created_at);
