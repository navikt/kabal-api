DELETE FROM klage.tildelinghistorikk;

ALTER TABLE klage.tildelinghistorikk
    ADD COLUMN fradeling_reason_id TEXT,
    ADD COLUMN utfoerende_ident TEXT NOT NULL;
