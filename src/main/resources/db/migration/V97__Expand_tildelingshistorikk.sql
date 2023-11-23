DELETE FROM klage.tildelinghistorikk;

ALTER TABLE klage.tildelinghistorikk
    ADD COLUMN reason TEXT,
    ADD COLUMN utfoerende_ident TEXT NOT NULL DEFAULT 'cannot happen';
