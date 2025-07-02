ALTER TABLE klage.behandling
    ADD COLUMN satt_paa_vent_reason_id TEXT;

UPDATE klage.behandling
SET satt_paa_vent_reason_id = '5'
WHERE satt_paa_vent_reason IS NOT NULL;

ALTER TABLE klage.satt_paa_vent_historikk
    ADD COLUMN satt_paa_vent_reason_id TEXT;

UPDATE klage.satt_paa_vent_historikk
SET satt_paa_vent_reason_id = '5'
WHERE satt_paa_vent_reason IS NOT NULL;

ALTER TABLE klage.behandling_aud
    ADD COLUMN satt_paa_vent_reason_id TEXT;

UPDATE klage.behandling_aud
SET satt_paa_vent_reason_id = '5'
WHERE satt_paa_vent_reason IS NOT NULL;

