CREATE TABLE klage.forlenget_behandlingstid_work_area
(
    id                          UUID PRIMARY KEY NOT NULL,
    behandling_id               UUID REFERENCES klage.behandling (id) ON DELETE CASCADE,
    created                     TIMESTAMP        NOT NULL,
    title                       TEXT,
    fullmektig_fritekst         TEXT,
    custom_text                 TEXT,
    reason                      TEXT,
    behandlingstid_units        INT,
    behandlingstid_unit_type_id TEXT,
    behandlingstid_date         DATE
);

CREATE TABLE klage.forlenget_behandlingstid_work_area_receiver
(
    id                                    UUID PRIMARY KEY NOT NULL,
    identifikator                         TEXT             NOT NULL,
    forlenget_behandlingstid_work_area_id UUID             NOT NULL
        CONSTRAINT fk_fbwa_receiver
            REFERENCES klage.forlenget_behandlingstid_work_area,
    local_print                           BOOLEAN DEFAULT FALSE,
    force_central_print                   BOOLEAN DEFAULT FALSE,
    address_adresselinje_1                TEXT,
    address_adresselinje_2                TEXT,
    address_adresselinje_3                TEXT,
    address_postnummer                    TEXT,
    address_poststed                      TEXT,
    address_landkode                      TEXT,
    navn                                  TEXT
);

--TODO update audit tables as well if needed