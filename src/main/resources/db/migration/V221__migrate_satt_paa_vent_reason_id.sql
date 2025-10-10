UPDATE klage.satt_paa_vent_historikk
SET satt_paa_vent_reason_id = '6'
WHERE satt_paa_vent_reason_id = '1'
  AND behandling_id IN (SELECT id FROM klage.behandling WHERE satt_paa_vent_reason_id = '1' AND type_id NOT IN ('2', '6'));

UPDATE klage.behandling_aud
SET satt_paa_vent_reason_id = '6'
WHERE satt_paa_vent_reason_id = '1'
  AND id IN (SELECT id FROM klage.behandling WHERE satt_paa_vent_reason_id = '1' AND type_id NOT IN ('2', '6'));

UPDATE klage.behandling
SET satt_paa_vent_reason_id = '6'
WHERE satt_paa_vent_reason_id = '1'
  AND type_id NOT IN ('2', '6');
