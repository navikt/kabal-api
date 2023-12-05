CREATE TABLE klage.satt_paa_vent_historikk
(
    id                   UUID PRIMARY KEY,
    behandling_id        UUID NOT NULL,
    satt_paa_vent_reason TEXT,
    satt_paa_vent_from   DATE,
    satt_paa_vent_to     DATE,
    tidspunkt            TIMESTAMP,
    utfoerende_ident     TEXT,
    CONSTRAINT fk_satt_paa_vent_historikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_satt_paa_vent_historikk_idx ON klage.satt_paa_vent_historikk (behandling_id);
