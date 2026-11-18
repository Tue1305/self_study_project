-- V15: Add customer loyalty points and salon gift card management
ALTER TABLE customer ADD COLUMN IF NOT EXISTS loyalty_points INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS gift_cards (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(64) NOT NULL,
    initial_balance NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    purchaser_name VARCHAR(128),
    purchaser_email VARCHAR(128),
    recipient_name VARCHAR(128),
    recipient_email VARCHAR(128),
    notes VARCHAR(1000),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gift_cards_tenant_code ON gift_cards (tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_gift_cards_tenant_status ON gift_cards (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_gift_cards_tenant_created ON gift_cards (tenant_id, created_at);

-- Enhance appointment_transactions with loyalty and gift card tracking
ALTER TABLE appointment_transactions ADD COLUMN IF NOT EXISTS points_earned INT DEFAULT 0;
ALTER TABLE appointment_transactions ADD COLUMN IF NOT EXISTS points_redeemed INT DEFAULT 0;
ALTER TABLE appointment_transactions ADD COLUMN IF NOT EXISTS points_discount_amount NUMERIC(10, 2) DEFAULT 0.00;
ALTER TABLE appointment_transactions ADD COLUMN IF NOT EXISTS gift_card_code VARCHAR(64);
ALTER TABLE appointment_transactions ADD COLUMN IF NOT EXISTS gift_card_amount NUMERIC(10, 2) DEFAULT 0.00;
