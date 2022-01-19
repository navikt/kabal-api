ALTER TABLE klage.mottak
    RENAME COLUMN avsender_saksbehandlerident TO forrige_saksbehandlerident;
ALTER TABLE klage.mottak
    RENAME COLUMN avsender_enhet TO forrige_behandlende_enhet;
ALTER TABLE klage.mottak
    RENAME COLUMN dato_mottatt_foersteinstans TO dato_brukers_henvendelse_mottatt_nav;
ALTER TABLE klage.mottak
    RENAME COLUMN dato_oversendt_klageinstans TO dato_sak_mottatt_klageinstans;

ALTER TABLE klage.mottak
    ADD COLUMN forrige_vedtak_dato DATE,
    ADD COLUMN forrige_vedtak_id   UUID