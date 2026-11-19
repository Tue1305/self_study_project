-- V19 Add Staff Days Off table for Admin Calendar & Booking Availability (PostgreSQL)

CREATE TABLE IF NOT EXISTS staff_days_off (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    staff_id BIGINT NOT NULL,
    staff_name VARCHAR(255),
    off_date DATE NOT NULL,
    reason VARCHAR(255) DEFAULT 'Day Off',
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_staff_day_off UNIQUE (tenant_id, staff_id, off_date)
);

CREATE INDEX IF NOT EXISTS idx_staff_day_off_tenant_date ON staff_days_off (tenant_id, off_date);
CREATE INDEX IF NOT EXISTS idx_staff_day_off_tenant_staff ON staff_days_off (tenant_id, staff_id);
