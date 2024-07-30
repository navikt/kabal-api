CREATE TABLE klage.varslet_behandlingstid_historikk
(
    id                                  UUID PRIMARY KEY,
    behandling_id                       UUID NOT NULL,
    tidspunkt                           TIMESTAMP,
    utfoerende_ident                    TEXT,
    utfoerende_navn                     TEXT,
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
    id                                              UUID PRIMARY KEY,
    varslet_behandlingstid_historikk_id             UUID NOT NULL,
    varslet_behandlingstid_historikk_mottaker_type  TEXT NOT NULL,
    varslet_behandlingstid_historikk_mottaker_value TEXT NOT NULL,
    FOREIGN KEY (varslet_behandlingstid_historikk_id)
        REFERENCES klage.varslet_behandlingstid_historikk (id)
);

CREATE INDEX behandling_varslet_behandlingstid_historikk_mottaker_idx ON klage.varslet_behandlingstid_historikk_mottaker_info (varslet_behandlingstid_historikk_id);

ALTER TABLE klage.fullmektighistorikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.klagerhistorikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.medunderskriverhistorikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.rolhistorikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.satt_paa_vent_historikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.tildelinghistorikk
    ADD COLUMN utfoerende_navn TEXT;

ALTER TABLE klage.behandling
    ADD COLUMN feilregistrering_navn TEXT;

ALTER TABLE klage.behandling
    ADD COLUMN ferdigstilling_nav_ident TEXT,
    ADD COLUMN ferdigstilling_navn      TEXT;
