-- V2 Multi-Tenant Architecture, Station Preference, and Luxe Tenant/Staff Seeds (PostgreSQL)

-- 1. Create tenant_configs table if it does not already exist
CREATE TABLE IF NOT EXISTS tenant_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_key VARCHAR(64) NOT NULL UNIQUE,
    custom_domain VARCHAR(160) UNIQUE,
    salon_name VARCHAR(120) NOT NULL DEFAULT 'Jenny''s Nail Studio',
    primary_color VARCHAR(20) NOT NULL DEFAULT '#e83e8c',
    secondary_color VARCHAR(20) NOT NULL DEFAULT '#343a40',
    font_family VARCHAR(120) NOT NULL DEFAULT '''Poppins'', sans-serif',
    logo_url VARCHAR(500),
    banner_image_url VARCHAR(500),
    step_order VARCHAR(120) NOT NULL DEFAULT 'SERVICE,STAFF,STATION,DETAILS',
    enable_station_selection BOOLEAN NOT NULL DEFAULT FALSE,
    available_stations VARCHAR(500) DEFAULT 'Main Floor Chair 1,Main Floor Chair 2,VIP Window Suite,Quiet Spa Room',
    enabled_categories VARCHAR(500) DEFAULT '',
    show_appointment_notes BOOLEAN NOT NULL DEFAULT TRUE,
    show_staff_qualification_tags BOOLEAN NOT NULL DEFAULT FALSE,
    layout_variant VARCHAR(40) NOT NULL DEFAULT 'classic',
    default_tenant BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. Add tenant_id and station_preference columns safely without dropping existing data
ALTER TABLE services ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS station_preference VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- 3. Backfill any existing rows missing a tenant_id
UPDATE services SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
UPDATE appointments SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
UPDATE users SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';

-- 4. Create indexes for tenant-scoped queries and double-booking conflict detection
CREATE INDEX IF NOT EXISTS idx_services_tenant_id ON services (tenant_id);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant_id ON appointments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_appointments_tenant_conflict ON appointments (tenant_id, nail_tech_id, appointment_time, status);

-- 5. Seed default and luxe tenant configurations idempotently
INSERT INTO tenant_configs (
    tenant_key, custom_domain, salon_name, primary_color, secondary_color,
    font_family, step_order, enable_station_selection, available_stations,
    enabled_categories, show_appointment_notes, show_staff_qualification_tags,
    layout_variant, default_tenant
)
VALUES (
    'default', 'jennysnailstudio.com', 'Jenny''s Nail Studio', '#e83e8c', '#343a40',
    '''Poppins'', sans-serif', 'SERVICE,STAFF,STATION,DETAILS', FALSE,
    'Main Floor Chair 1,Main Floor Chair 2,VIP Window Suite,Quiet Spa Room',
    '', TRUE, FALSE, 'classic', TRUE
)
ON CONFLICT (tenant_key) DO NOTHING;

INSERT INTO tenant_configs (
    tenant_key, custom_domain, salon_name, primary_color, secondary_color,
    font_family, step_order, enable_station_selection, available_stations,
    enabled_categories, show_appointment_notes, show_staff_qualification_tags,
    layout_variant, default_tenant
)
VALUES (
    'luxe', 'luxe.jennysnailstudio.com', 'Luxe Nail & Spa Sanctuary', '#00b4d8', '#03045e',
    '''Playfair Display'', serif', 'SERVICE,STAFF,STATION,DETAILS', TRUE,
    'Private Suite A,Private Suite B,Oceanfront Spa Chair,VIP Nail Bar',
    '', TRUE, FALSE, 'blue-sky', FALSE
)
ON CONFLICT (tenant_key) DO NOTHING;

-- 6. Seed Luxe services idempotently
INSERT INTO services (tenant_id, name, description, price, duration_minutes)
SELECT 'luxe', 'Luxe Signature Gel Manicure', 'Organic cuticle ritual, warm towel wrap, and high-gloss Luxe gel polish.', 48.00, 40
WHERE NOT EXISTS (SELECT 1 FROM services WHERE tenant_id = 'luxe' AND name = 'Luxe Signature Gel Manicure');

INSERT INTO services (tenant_id, name, description, price, duration_minutes)
SELECT 'luxe', 'Royal Lavender Spa Pedicure', 'Mineral bath soak, volcanic stone scrub, paraffin mask, and lavender foot massage.', 72.00, 55
WHERE NOT EXISTS (SELECT 1 FROM services WHERE tenant_id = 'luxe' AND name = 'Royal Lavender Spa Pedicure');

-- 7. Seed Luxe staff idempotently
INSERT INTO users (tenant_id, username, name, email, specialty, role, password)
VALUES
    ('luxe', 'sophia@luxenailspa.com', 'Sophia Laurent', 'sophia@luxenailspa.com', 'ALL, NAIL_ART', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S'),
    ('luxe', 'chloe@luxenailspa.com', 'Chloe Vance', 'chloe@luxenailspa.com', 'MANICURE, FACIAL', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S'),
    ('luxe', 'marcus@luxenailspa.com', 'Marcus Reed', 'marcus@luxenailspa.com', 'BODY_MASSAGE, MANICURE', 'ROLE_NAIL_TECH', '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S')
ON CONFLICT (username) DO NOTHING;
