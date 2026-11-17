-- V9__prevent_double_booking_slot.sql
-- Prevent concurrent double-booking of active appointment slots for the same technician within a salon tenant

-- In case existing test or development data has duplicate active records, retain the latest record
DELETE FROM appointments a
USING appointments b
WHERE a.id < b.id
  AND a.tenant_id = b.tenant_id
  AND a.nail_tech_id IS NOT NULL
  AND a.nail_tech_id = b.nail_tech_id
  AND a.appointment_time = b.appointment_time
  AND a.status != 'CANCELLED'
  AND b.status != 'CANCELLED';

-- Partial unique index ensuring no two active (non-cancelled) appointments share the same technician and time slot
CREATE UNIQUE INDEX IF NOT EXISTS uk_appointments_active_tech_slot
ON appointments (tenant_id, nail_tech_id, appointment_time)
WHERE status != 'CANCELLED' AND nail_tech_id IS NOT NULL;
