UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'V135783',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = 'b4b120fb-39ed-4c7c-a372-47ea584fee05';

UPDATE klage.behandling
SET utfall_id = '4'
WHERE id = 'aaa9209a-d53f-4375-bf4b-ffa077917fef';