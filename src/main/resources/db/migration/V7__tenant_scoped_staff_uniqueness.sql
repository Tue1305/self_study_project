-- V7 Tenant-Scoped Staff Uniqueness (PostgreSQL)
-- Allows different tenants to independently register staff members with the same username or email,
-- while enforcing composite uniqueness within each tenant: UNIQUE (tenant_id, username) and UNIQUE (tenant_id, email).

-- 1. Ensure tenant_id column is properly defaulted and non-null
UPDATE users SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
ALTER TABLE users ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- 2. Drop any legacy single-column unique constraints on username or email
DO $$
DECLARE
    con_record RECORD;
BEGIN
    FOR con_record IN (
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'users'
          AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND (
              SELECT attname FROM pg_attribute
              WHERE attrelid = rel.oid AND attnum = con.conkey[1]
          ) IN ('username', 'email')
    ) LOOP
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT IF EXISTS ' || quote_ident(con_record.conname);
    END LOOP;
END $$;

-- 3. Drop any legacy single-column unique indexes on username or email
DO $$
DECLARE
    idx_record RECORD;
BEGIN
    FOR idx_record IN (
        SELECT idx.relname
        FROM pg_index i
        JOIN pg_class tbl ON tbl.oid = i.indrelid
        JOIN pg_class idx ON idx.oid = i.indexrelid
        WHERE tbl.relname = 'users'
          AND i.indisunique = true
          AND array_length(i.indkey, 1) = 1
          AND (
              SELECT attname FROM pg_attribute
              WHERE attrelid = tbl.oid AND attnum = i.indkey[0]
          ) IN ('username', 'email')
          AND NOT EXISTS (
              SELECT 1 FROM pg_constraint con WHERE con.conindid = idx.oid
          )
    ) LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(idx_record.relname);
    END LOOP;
END $$;

-- Explicit drops for standard known constraint names
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_username;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_email;

-- 4. Add composite tenant-scoped unique constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_tenant_username'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_tenant_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email);
    END IF;
END $$;

-- 5. Add performance indexes for tenant-scoped user lookups
CREATE INDEX IF NOT EXISTS idx_users_tenant_username ON users (tenant_id, username);
CREATE INDEX IF NOT EXISTS idx_users_tenant_email ON users (tenant_id, email);
