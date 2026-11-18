-- V13__add_staff_commission_rate.sql
-- Add commission_rate column to users table for nail technicians and staff commission splitting
-- Default: 60.00 (60% to technician, 40% to salon house)

ALTER TABLE users ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5, 2) NOT NULL DEFAULT 60.00;
