CREATE TABLE klage.innholdsfortegnelse
(
    id               UUID PRIMARY KEY,
    mellomlager_id   TEXT      NOT NULL,
    hoveddokument_id UUID,
    created          TIMESTAMP NOT NULL,
    modified         TIMESTAMP NOT NULL,
    CONSTRAINT fk_innholdsfortegnelse_dokument_under_arbeid
        FOREIGN KEY (hoveddokument_id)
            REFERENCES klage.dokument_under_arbeid (id)
);
