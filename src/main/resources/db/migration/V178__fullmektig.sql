ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    ADD COLUMN navn TEXT;

ALTER TABLE klage.behandling
    ADD COLUMN prosessfullmektig_navn                   TEXT,
    ADD COLUMN prosessfullmektig_address_adressetype    TEXT,
    ADD COLUMN prosessfullmektig_address_adresselinje_1 TEXT,
    ADD COLUMN prosessfullmektig_address_adresselinje_2 TEXT,
    ADD COLUMN prosessfullmektig_address_adresselinje_3 TEXT,
    ADD COLUMN prosessfullmektig_address_postnummer     TEXT,
    ADD COLUMN prosessfullmektig_address_poststed       TEXT,
    ADD COLUMN prosessfullmektig_address_landkode       TEXT;

ALTER TABLE klage.behandling
    RENAME COLUMN klager_prosessfullmektig_type TO prosessfullmektig_type;

ALTER TABLE klage.behandling
    RENAME COLUMN klager_prosessfullmektig_value TO prosessfullmektig_value;

ALTER TABLE klage.varslet_behandlingstid_historikk_mottaker_info
    ADD COLUMN varslet_behandlingstid_historikk_mottaker_navn TEXT;