ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    ADD COLUMN force_central_print BOOLEAN DEFAULT false,
    ADD COLUMN adress_adresselinje_1 TEXT,
    ADD COLUMN adress_adresselinje_2 TEXT,
    ADD COLUMN adress_adresselinje_3 TEXT,
    ADD COLUMN adress_postnummer TEXT,
    ADD COLUMN adress_poststed TEXT,
    ADD COLUMN adress_landkode TEXT;