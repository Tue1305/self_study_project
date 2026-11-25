-- Per-staff daily working shift hours (HH:mm format, e.g. 10:00 to 16:00).
-- NULL means the technician works standard salon hours (10:00 - 19:00).
ALTER TABLE users ADD COLUMN IF NOT EXISTS shift_start_time VARCHAR(8);
ALTER TABLE users ADD COLUMN IF NOT EXISTS shift_end_time VARCHAR(8);
