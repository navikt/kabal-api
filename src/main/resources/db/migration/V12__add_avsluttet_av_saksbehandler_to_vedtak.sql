ALTER TABLE klage.vedtak
    ADD COLUMN avsluttet_av_saksbehandler TEXT;

ALTER TABLE klage.brevmottaker
    ADD COLUMN ferdigstilt_i_joark TIMESTAMP;