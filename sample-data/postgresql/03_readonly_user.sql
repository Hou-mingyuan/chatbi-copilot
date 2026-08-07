-- Demo query account: database-enforced SELECT-only access.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'chatbi_ro') THEN
        CREATE ROLE chatbi_ro LOGIN PASSWORD 'ChatBI!Readonly123';
    END IF;
END
$$;

ALTER ROLE chatbi_ro NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
ALTER ROLE chatbi_ro SET default_transaction_read_only = on;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM chatbi_ro;
GRANT CONNECT ON DATABASE chatbi_demo TO chatbi_ro;
GRANT USAGE ON SCHEMA public TO chatbi_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO chatbi_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO chatbi_ro;
