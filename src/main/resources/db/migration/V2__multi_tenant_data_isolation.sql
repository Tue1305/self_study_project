-- Multi-Tenant Data Isolation Migration (PostgreSQL)
-- 1. Add tenant_id column to services, appointments, and users tables
ALTER TABLE services ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- 2. Backfill existing records without a tenant assignment
UPDATE services SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
UPDATE appointments SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
UPDATE users SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';

-- 3. Create indexes for fast tenant-scoped queries and conflict detection
CREATE INDEX IF NOT EXISTS idx_services_tenant_id ON services (tenant_id);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant_id ON appointments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant_conflict ON appointments (tenant_id, nail_tech_id, appointment_time, status);

-- 4. Seed sample Luxe tenant services and technicians for isolated multi-tenant testing
INSERT INTO services (tenant_id, name, description, price, duration_minutes)
SELECT 'luxe', 'Luxe Signature Gel Manicure', 'Organic cuticle ritual, warm towel wrap, and high-gloss Luxe gel polish.', 48.00, 40
WHERE NOT EXISTS (SELECT 1 FROM services WHERE tenant_id = 'luxe');

INSERT INTO services (tenant_id, name, description, price, duration_minutes)
SELECT 'luxe', 'Royal Lavender Spa Pedicure', 'Mineral bath soak, volcanic stone scrub, paraffin mask, and lavender foot massage.', 72.00, 55
WHERE NOT EXISTS (SELECT 1 FROM services WHERE tenant_id = 'luxe' AND name = 'Royal Lavender Spa Pedicure');

INSERT INTO users (tenant_id, username, name, email, specialty, role, password)
VALUES
    ('luxe', 'sophia@luxenailspa.com', 'Sophia Laurent', 'sophia@luxenailspa.com', 'ALL, NAIL_ART', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S'),
    ('luxe', 'chloe@luxenailspa.com', 'Chloe Vance', 'chloe@luxenailspa.com', 'MANICURE, FACIAL', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S'),
    ('luxe', 'marcus@luxenailspa.com', 'Marcus Reed', 'marcus@luxenailspa.com', 'BODY_MASSAGE, MANICURE', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S')
ON CONFLICT (username) DO UPDATE SET tenant_id = EXCLUDED.tenant_id;
