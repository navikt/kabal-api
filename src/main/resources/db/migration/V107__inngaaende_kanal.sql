ALTER TABLE klage.dokument_under_arbeid
    ADD COLUMN inngaaende_kanal text;

ALTER TABLE klage.dokument_under_arbeid_brevmottaker_info
    RENAME TO dokument_under_arbeid_avsender_mottaker_info;
