ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    ALTER COLUMN identifikator drop not null;

ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info_aud
    ALTER COLUMN identifikator drop not null;

ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    ADD COLUMN technical_part_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info_aud
    ADD COLUMN technical_part_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN saken_gjelder_id UUID DEFAULT gen_random_uuid();

ALTER TABLE klage.behandling
    ADD COLUMN klager_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN prosessfullmektig_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN prosessfullmektig_id UUID;

--populate the new id columns. If value is the same, use the same id, per behandling.
-- UPDATE klage.behandling
-- SET klager_id = saken_gjelder_id
