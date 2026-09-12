DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'postgres')
        THEN
            GRANT USAGE ON SCHEMA flyway_history_schema TO postgres;
            GRANT USAGE ON SCHEMA klage TO postgres;
            GRANT SELECT ON ALL TABLES IN SCHEMA flyway_history_schema TO postgres;
            GRANT SELECT ON ALL TABLES IN SCHEMA klage TO postgres;
            ALTER DEFAULT PRIVILEGES IN SCHEMA flyway_history_schema GRANT SELECT ON TABLES TO postgres;
            ALTER DEFAULT PRIVILEGES IN SCHEMA klage GRANT SELECT ON TABLES TO postgres;
        END IF;
    END
$$;
