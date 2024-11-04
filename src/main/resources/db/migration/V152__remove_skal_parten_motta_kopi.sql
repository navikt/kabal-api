ALTER TABLE klage.mottak
    DROP COLUMN saken_gjelder_skal_motta_kopi;

ALTER TABLE klage.mottak
    DROP COLUMN klager_skal_motta_kopi;

ALTER TABLE klage.behandling
    DROP COLUMN saken_gjelder_skal_motta_kopi;

ALTER TABLE klage.behandling
    DROP COLUMN klager_skal_motta_kopi;

