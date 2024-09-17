ALTER TABLE klage.behandling
    ADD COLUMN ny_behandling_etter_tr_opphevet TIMESTAMP;

ALTER TABLE klage.behandling
    RENAME COLUMN ny_behandling_ka TO ny_ankebehandling_ka;