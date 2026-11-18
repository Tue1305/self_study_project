-- V11__add_customer_safety_and_color_formula.sql
-- Add medical flags / allergy alerts and color/product formula tracking to customer and appointment tables

ALTER TABLE customer
ADD COLUMN IF NOT EXISTS medical_flags VARCHAR(1000),
ADD COLUMN IF NOT EXISTS color_formula VARCHAR(1000);

ALTER TABLE appointments
ADD COLUMN IF NOT EXISTS medical_flags VARCHAR(500),
ADD COLUMN IF NOT EXISTS color_formula VARCHAR(500);
