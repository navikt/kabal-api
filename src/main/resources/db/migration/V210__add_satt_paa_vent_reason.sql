ALTER TABLE klage.behandling
    ADD COLUMN satt_paa_vent_reason_id TEXT DEFAULT '9'; --Set all existing records to '9' (other) as default;

ALTER TABLE klage.satt_paa_vent_historikk
    ADD COLUMN satt_paa_vent_reason_id TEXT DEFAULT '9'; --Set all existing records to '9' (other) as default

ALTER TABLE klage.behandling_aud
    ADD COLUMN satt_paa_vent_reason_id TEXT;

