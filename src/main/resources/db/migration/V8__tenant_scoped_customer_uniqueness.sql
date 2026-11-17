-- V8 Tenant-Scoped Customer Uniqueness (PostgreSQL)
-- Allows different tenants to independently register or sync customers with the same email,
-- while enforcing composite uniqueness within each tenant: UNIQUE (tenant_id, email).

-- 1. Ensure tenant_id column is properly defaulted and non-null in customer table
UPDATE customer SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';
ALTER TABLE customer ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE customer ALTER COLUMN tenant_id SET NOT NULL;

-- 2. Drop any legacy single-column unique constraints on email in customer table
DO $$
DECLARE
    con_record RECORD;
BEGIN
    FOR con_record IN (
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'customer'
          AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND (
              SELECT attname FROM pg_attribute
              WHERE attrelid = rel.oid AND attnum = con.conkey[1]
          ) = 'email'
    ) LOOP
        EXECUTE 'ALTER TABLE customer DROP CONSTRAINT IF EXISTS ' || quote_ident(con_record.conname);
    END LOOP;
END $$;

-- 3. Drop any legacy single-column unique indexes on email in customer table
DO $$
DECLARE
    idx_record RECORD;
BEGIN
    FOR idx_record IN (
        SELECT idx.relname
        FROM pg_index i
        JOIN pg_class tbl ON tbl.oid = i.indrelid
        JOIN pg_class idx ON idx.oid = i.indexrelid
        WHERE tbl.relname = 'customer'
          AND i.indisunique = true
          AND array_length(i.indkey, 1) = 1
          AND (
              SELECT attname FROM pg_attribute
              WHERE attrelid = tbl.oid AND attnum = i.indkey[0]
          ) = 'email'
          AND NOT EXISTS (
              SELECT 1 FROM pg_constraint con WHERE con.conindid = idx.oid
          )
    ) LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(idx_record.relname);
    END LOOP;
END $$;

-- Explicit drops for standard known constraint names on customer table
ALTER TABLE customer DROP CONSTRAINT IF EXISTS customer_email_key;
ALTER TABLE customer DROP CONSTRAINT IF EXISTS uk_customer_email;

-- 4. Add composite tenant-scoped unique constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_customer_tenant_email'
    ) THEN
        ALTER TABLE customer ADD CONSTRAINT uk_customer_tenant_email UNIQUE (tenant_id, email);
    END IF;
END $$;

-- 5. Add performance indexes for tenant-scoped customer lookups
CREATE INDEX IF NOT EXISTS idx_customer_tenant_email ON customer (tenant_id, email);
