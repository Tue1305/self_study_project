-- V17 Add End-of-Day (EOD) Register Closes and Reconciliation (PostgreSQL)

CREATE TABLE IF NOT EXISTS eod_register_closes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    closing_date DATE NOT NULL,
    closed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by VARCHAR(120) NOT NULL DEFAULT 'OWNER',
    opening_cash_float NUMERIC(10, 2) NOT NULL DEFAULT 200.00,
    expected_cash NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    actual_cash_counted NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    cash_variance NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    card_batch_total NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    gift_card_redemptions NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_service_sales NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_tax_collected NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_tips_collected NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_discounts_given NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_gross_revenue NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_commission_paid NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_salon_house_share NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    transaction_count INT NOT NULL DEFAULT 0,
    notes TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
    summary_json TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eod_tenant_date ON eod_register_closes (tenant_id, closing_date);
CREATE INDEX IF NOT EXISTS idx_eod_tenant_closed_at ON eod_register_closes (tenant_id, closed_at);
