ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info
    RENAME TO brevmottaker;

ALTER TABLE klage.dokument_under_arbeid_avsender_mottaker_info_aud
    RENAME TO brevmottaker_aud;

ALTER TABLE klage.dua_dokument_under_arbeid_avsender_mottaker_info_aud
    RENAME TO dua_brevmottaker_aud;

ALTER TABLE klage.brevmottaker
    ALTER COLUMN identifikator DROP NOT NULL;

ALTER TABLE klage.brevmottaker
    ALTER COLUMN dokument_under_arbeid_id DROP NOT NULL;

ALTER TABLE klage.brevmottaker_aud
    ALTER COLUMN identifikator DROP NOT NULL;

ALTER TABLE klage.brevmottaker
    ADD COLUMN forlenget_behandlingstid_draft_id UUID,
    ADD COLUMN technical_part_id                 UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE klage.brevmottaker
    ADD CONSTRAINT fk_forlenget_behandlingstid_draft FOREIGN KEY (forlenget_behandlingstid_draft_id) REFERENCES klage.forlenget_behandlingstid_draft;

CREATE INDEX fk_forlenget_behandlingstid_draft_idx ON klage.brevmottaker (forlenget_behandlingstid_draft_id);

ALTER TABLE klage.brevmottaker_aud
    ADD COLUMN technical_part_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN saken_gjelder_id UUID NOT NULL DEFAULT gen_random_uuid(); --need these b/c field is not nullable in Kotlin.

ALTER TABLE klage.behandling_aud
    ADD COLUMN saken_gjelder_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN klager_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE klage.behandling_aud
    ADD COLUMN klager_id UUID;

ALTER TABLE klage.behandling
    ADD COLUMN prosessfullmektig_id UUID;

ALTER TABLE klage.behandling_aud
    ADD COLUMN prosessfullmektig_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN saken_gjelder_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN klager_id UUID;

ALTER TABLE klage.mottak
    ADD COLUMN prosessfullmektig_id UUID;

--copy all rows from varslet_behandlingstid_historikk_mottaker_info to dokument_under_arbeid_avsender_mottaker_info (brevmottaker)
INSERT INTO klage.brevmottaker(id,
                               identifikator,
                               dokument_under_arbeid_id,
                               local_print,
                               force_central_print,
                               address_adresselinje_1,
                               address_adresselinje_2,
                               address_adresselinje_3,
                               address_postnummer,
                               address_poststed,
                               address_landkode,
                               navn,
                               forlenget_behandlingstid_draft_id,
                               technical_part_id)
SELECT gen_random_uuid(),
       identifikator,
       NULL,
       local_print,
       force_central_print,
       address_adresselinje_1,
       address_adresselinje_2,
       address_adresselinje_3,
       address_postnummer,
       address_poststed,
       address_landkode,
       navn,
       forlenget_behandlingstid_draft_id,
       gen_random_uuid()
FROM klage.forlenget_behandlingstid_draft_receiver;

DROP TABLE klage.forlenget_behandlingstid_draft_receiver;

UPDATE klage.behandling
SET prosessfullmektig_id = gen_random_uuid()
WHERE prosessfullmektig_value IS NOT NULL;