ALTER TABLE klage.behandling
    ADD COLUMN kant_reference UUID;

ALTER TABLE klage.behandling_aud
    ADD COLUMN kant_reference UUID;
