CREATE TABLE klage.klagerhistorikk
(
    id               UUID PRIMARY KEY,
    behandling_id    UUID NOT NULL,
    klager_value     TEXT,
    klager_type      TEXT,
    tidspunkt        TIMESTAMP,
    utfoerende_ident TEXT,
    CONSTRAINT fk_klagerhistorikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_klagerhistorikk_idx ON klage.klagerhistorikk (behandling_id);

CREATE TABLE klage.fullmektighistorikk
(
    id               UUID PRIMARY KEY,
    behandling_id    UUID NOT NULL,
    fullmektig_value TEXT,
    fullmektig_type  TEXT,
    tidspunkt        TIMESTAMP,
    utfoerende_ident TEXT,
    CONSTRAINT fk_fullmektighistorikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_fullmektighistorikk_idx ON klage.fullmektighistorikk (behandling_id);