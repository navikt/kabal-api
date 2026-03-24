-- Sak 1 fra felles dokument sendt til patching i Infotrygd

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'V135783',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = 'ea32468a-514e-45a6-9c98-8b7a049ffd2d';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'V135783',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, skulle ikke vært opprettet',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = 'e21480d8-9640-42b7-91be-4e8835814aeb';

-- Feil av saksbehandler meldt på Slack
UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'W132204',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, skulle ikke opprettet anke',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = 'cb0e458d-8e54-4c97-9803-97bc7cbecc6f';

--Gjenåpning av ankeITR
UPDATE klage.behandling
SET dato_behandling_avsluttet_av_saksbehandler = null,
    dato_behandling_avsluttet                  = null,
    ny_ankebehandling_ka                       = null,
    ferdigstilling_navn                        = null,
    ferdigstilling_nav_ident                   = null
WHERE id = 'b766c95e-dd9e-4b9c-ab57-ce4c93f43536';