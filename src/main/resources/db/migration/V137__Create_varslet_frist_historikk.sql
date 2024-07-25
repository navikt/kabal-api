CREATE TABLE klage.varslet_frist_historikk
(
    id                  UUID PRIMARY KEY,
    behandling_id       UUID NOT NULL,
    mottaker_value      TEXT,
    mottaker_type       TEXT,
    tidspunkt           TIMESTAMP,
    utfoerende_ident    TEXT,
    varslet_frist       DATE,
    varslet_frist_units INT,
    varslet_frist_id    TEXT,
    CONSTRAINT fk_varslet_frist_historikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_varslet_frist_historikk_idx ON klage.varslet_frist_historikk (behandling_id);

