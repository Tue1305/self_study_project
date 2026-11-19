-- V18 Add Client Digital Nail Profile columns and Nail History / Color Notes table (PostgreSQL)

ALTER TABLE customer ADD COLUMN IF NOT EXISTS nail_shape VARCHAR(64);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS nail_length VARCHAR(64);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS nail_system VARCHAR(64);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS favorite_colors VARCHAR(1000);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS cuticle_care_notes VARCHAR(1000);

CREATE TABLE IF NOT EXISTS customer_nail_note (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    customer_id BIGINT NOT NULL,
    appointment_id BIGINT,
    technician_name VARCHAR(255),
    service_date DATE,
    nail_shape VARCHAR(64),
    nail_length VARCHAR(64),
    nail_system VARCHAR(64),
    color_details VARCHAR(1000),
    notes VARCHAR(2000),
    photo_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nail_note_tenant_cust ON customer_nail_note (tenant_id, customer_id);
