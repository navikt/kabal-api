ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    ADD COLUMN force_central_print BOOLEAN DEFAULT false,
    ADD COLUMN address_adresselinje_1 TEXT,
    ADD COLUMN address_adresselinje_2 TEXT,
    ADD COLUMN address_adresselinje_3 TEXT,
    ADD COLUMN address_postnummer TEXT,
    ADD COLUMN address_poststed TEXT,
    ADD COLUMN address_landkode TEXT;