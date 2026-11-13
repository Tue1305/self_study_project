-- V6 Add reminder_sent_at column for automated 24-hour appointment reminder tracking
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP(6);
CREATE INDEX IF NOT EXISTS idx_appointments_reminder_sent_at ON appointments (reminder_sent_at);
