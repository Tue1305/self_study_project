-- V5 Add cancellation_token for self-service cancellation and reschedule links
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS cancellation_token VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_appointments_cancellation_token ON appointments (cancellation_token);
