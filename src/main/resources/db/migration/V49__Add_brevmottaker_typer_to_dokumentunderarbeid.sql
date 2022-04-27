CREATE TABLE klage.dokument_under_arbeid_brevmottaker_type
(
    id                       TEXT NOT NULL,
    dokument_under_arbeid_id UUID NOT NULL,
    PRIMARY KEY (id, dokument_under_arbeid_id),
    CONSTRAINT fk_brevmottaker_type_dokument_under_arbeid
        FOREIGN KEY (dokument_under_arbeid_id)
            REFERENCES klage.dokument_under_arbeid (id)
);