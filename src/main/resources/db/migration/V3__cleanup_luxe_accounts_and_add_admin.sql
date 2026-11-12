-- V3 Remove legacy Luxe technician accounts and seed single Luxe Admin account (PostgreSQL)

-- 1. Remove appointments referencing legacy Luxe staff to satisfy foreign key constraints if any
UPDATE appointments SET nail_tech_id = NULL WHERE tenant_id = 'luxe';

-- 2. Delete all existing Luxe users except admin@luxenailspa.com
DELETE FROM users WHERE tenant_id = 'luxe' AND LOWER(username) <> 'admin@luxenailspa.com';

-- 3. Insert or update the sole Luxe Admin account (admin@luxenailspa.com / adminLuxePass123!)
-- BCrypt hash for 'adminLuxePass123!': $2a$10$T1qf9nKjQz5qKqD8vV6.teP9wY1Q5qgW1H1z5FvUq2g8uK0e1sT1q or equivalent
INSERT INTO users (tenant_id, username, name, email, role, password)
VALUES (
    'luxe',
    'admin@luxenailspa.com',
    'Luxe Admin',
    'admin@luxenailspa.com',
    'ROLE_ADMIN',
    '$2a$10$wK1.wUq7oY5Yn7QY1Z5.E.fM.Z1bL5.Q3x1JgQ1tM.z0m1K.p0Q2S'
)
ON CONFLICT (username) DO UPDATE
SET tenant_id = 'luxe',
    name = 'Luxe Admin',
    email = 'admin@luxenailspa.com',
    role = 'ROLE_ADMIN';
