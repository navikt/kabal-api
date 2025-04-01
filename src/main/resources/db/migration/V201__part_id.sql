ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    RENAME TO brevmottaker;

ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info_aud
    RENAME TO brevmottaker_aud;


ALTER TABLE klage.dua_dokument_under_arbeid_avsender_mottaker_info_aud
    RENAME TO dua_brevmottaker_aud;

--delete table varslet_behandlingstid_historikk_mottaker_info
--copy data first?

ALTER TABLE klage.brevmottaker
    ALTER COLUMN identifikator drop not null;

ALTER TABLE klage.brevmottaker_aud
    ALTER COLUMN identifikator drop not null;

ALTER TABLE klage.brevmottaker
    ADD COLUMN forlenget_behandlingstid_draft_id UUID,
    ADD COLUMN technical_part_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE klage.brevmottaker
    ADD CONSTRAINT fk_forlenget_behandlingstid_draft foreign key (forlenget_behandlingstid_draft_id) references klage.forlenget_behandlingstid_draft;

CREATE INDEX fk_forlenget_behandlingstid_draft_idx ON klage.brevmottaker (forlenget_behandlingstid_draft_id);

ALTER TABLE klage.brevmottaker_aud
    ADD COLUMN technical_part_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN saken_gjelder_id UUID DEFAULT gen_random_uuid();

ALTER TABLE klage.behandling
    ADD COLUMN klager_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN saken_gjelder_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN klager_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN prosessfullmektig_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN prosessfullmektig_id UUID;

--populate the new id columns. If value is the same, use the same id, per behandling.
-- UPDATE klage.behandling
-- SET klager_id = saken_gjelder_id
