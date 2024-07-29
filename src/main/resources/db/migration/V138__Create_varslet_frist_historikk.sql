CREATE TABLE klage.varslet_behandlingstid_historikk
(
    id                                  UUID PRIMARY KEY,
    behandling_id                       UUID NOT NULL,
    tidspunkt                           TIMESTAMP,
    utfoerende_ident                    TEXT,
    varslet_frist                       DATE,
    varslet_behandlingstid_units        INT,
    varslet_behandlingstid_unit_type_id TEXT,
    CONSTRAINT fk_varslet_behandlingstid_historikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_varslet_behandlingstid_historikk_idx ON klage.varslet_behandlingstid_historikk (behandling_id);

CREATE TABLE klage.varslet_behandlingstid_historikk_mottaker_info
(
    varslet_behandlingstid_historikk_id             UUID NOT NULL,
    varslet_behandlingstid_historikk_mottaker_type  TEXT NOT NULL,
    varslet_behandlingstid_historikk_mottaker_value TEXT NOT NULL,
    PRIMARY KEY (varslet_behandlingstid_historikk_mottaker_type, varslet_behandlingstid_historikk_mottaker_value,
                 varslet_behandlingstid_historikk_id),
    FOREIGN KEY (varslet_behandlingstid_historikk_id)
        REFERENCES klage.varslet_behandlingstid_historikk (id)
)