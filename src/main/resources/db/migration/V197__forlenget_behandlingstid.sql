CREATE TABLE klage.forlenget_behandlingstid_draft
(
    id                           UUID PRIMARY KEY NOT NULL,
    created                      TIMESTAMP        NOT NULL,
    title                        TEXT,
    fullmektig_fritekst          TEXT,
    custom_text                  TEXT,
    reason                       TEXT,
    previous_behandlingstid_info TEXT,
    behandlingstid_units         INT,
    behandlingstid_unit_type_id  TEXT,
    behandlingstid_date          DATE
);

CREATE TABLE klage.forlenget_behandlingstid_draft_receiver
(
    id                                UUID PRIMARY KEY NOT NULL,
    identifikator                     TEXT             NOT NULL,
    forlenget_behandlingstid_draft_id UUID             NOT NULL
        CONSTRAINT fk_fbd_receiver
            REFERENCES klage.forlenget_behandlingstid_draft,
    local_print                       BOOLEAN DEFAULT FALSE,
    force_central_print               BOOLEAN DEFAULT FALSE,
    address_adresselinje_1            TEXT,
    address_adresselinje_2            TEXT,
    address_adresselinje_3            TEXT,
    address_postnummer                TEXT,
    address_poststed                  TEXT,
    address_landkode                  TEXT,
    navn                              TEXT
);

ALTER TABLE klage.behandling
    ADD COLUMN forlenget_behandlingstid_draft_id UUID REFERENCES klage.forlenget_behandlingstid_draft (id);

ALTER TABLE klage.behandling_aud
    ADD COLUMN forlenget_behandlingstid_draft_id UUID;