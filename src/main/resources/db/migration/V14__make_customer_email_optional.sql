-- V14__make_customer_email_optional.sql
-- Allow appointments to be booked without requiring a customer email address
ALTER TABLE appointments ALTER COLUMN customer_email DROP NOT NULL;
