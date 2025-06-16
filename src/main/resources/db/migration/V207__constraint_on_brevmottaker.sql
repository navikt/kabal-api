ALTER TABLE klage.brevmottaker
    ADD CONSTRAINT unique_mottaker UNIQUE (dokument_under_arbeid_id, technical_part_id);