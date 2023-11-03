ALTER TABLE klage.dokument_under_arbeid_journalpost_id
    ADD COLUMN dokument_info_id TEXT;

ALTER TABLE klage.dokument_under_arbeid_journalpost_id
    RENAME TO dokument_under_arbeid_dokarkiv_reference;