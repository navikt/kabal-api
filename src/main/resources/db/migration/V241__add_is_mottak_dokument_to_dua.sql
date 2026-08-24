ALTER TABLE klage.dokument_under_arbeid
    ADD COLUMN is_mottak_dokument BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE klage.dokument_under_arbeid_aud
    ADD COLUMN is_mottak_dokument BOOLEAN;
