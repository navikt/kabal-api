ALTER TABLE klage.innholdsfortegnelse
    ADD CONSTRAINT unique_innholdsfortegnelse UNIQUE (hoveddokument_id);
