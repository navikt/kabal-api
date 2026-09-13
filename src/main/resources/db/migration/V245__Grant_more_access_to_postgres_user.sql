DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'postgres')
        THEN
            GRANT SELECT ON ALL SEQUENCES IN SCHEMA flyway_history_schema TO postgres;
            GRANT SELECT ON ALL SEQUENCES IN SCHEMA klage TO postgres;
            ALTER DEFAULT PRIVILEGES IN SCHEMA flyway_history_schema GRANT SELECT ON SEQUENCES TO postgres;
            ALTER DEFAULT PRIVILEGES IN SCHEMA klage GRANT SELECT ON SEQUENCES TO postgres;
        END IF;
    END
$$;
