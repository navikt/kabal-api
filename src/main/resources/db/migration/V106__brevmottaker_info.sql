ALTER TABLE klage.dokument_under_arbeid_brevmottaker_ident
    ADD COLUMN id UUID default gen_random_uuid(),
    ADD COLUMN local_print BOOLEAN default false;

ALTER TABLE klage.dokument_under_arbeid_brevmottaker_ident
    DROP CONSTRAINT dokument_under_arbeid_brevmottaker_ident_pkey;

ALTER TABLE klage.dokument_under_arbeid_brevmottaker_ident
    RENAME TO dokument_under_arbeid_brevmottaker_info;

ALTER TABLE klage.dokument_under_arbeid_brevmottaker_info
    ADD PRIMARY KEY (id);

