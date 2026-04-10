-- Sak 2 fra felles dokument sendt til patching i Infotrygd

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'W161655',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '280bfd97-cbf5-4d5f-9fbf-d2e3a9580112';

UPDATE klage.behandling
SET utfall_id                                  = '4',
    dato_behandling_avsluttet_av_saksbehandler = '2024-12-17 13:47:48.499497',
    dato_behandling_avsluttet                  = '2024-12-17 13:47:48.499497'
WHERE id = '383b42ba-9bd0-44ea-85ed-b91b820014cb';

-- Sak 3 fra felles dokument sendt til patching i Infotrygd

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'R169190',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '376ff2d1-3075-4649-b650-96191a9dabac';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'R169190',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '38f344e8-8d2b-44c0-9e02-77b876f576cb';

UPDATE klage.behandling
SET utfall_id                                  = '3',
    dato_behandling_avsluttet_av_saksbehandler = '2025-01-03 13:47:48.499497',
    dato_behandling_avsluttet                  = '2025-01-03 13:47:48.499497'
WHERE id = 'b0edff00-fd73-4bc5-9e25-057ff2eb83ed';
