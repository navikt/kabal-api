-- Sak 4 fra felles dokument sendt til patching i Infotrygd, sett utfall til Opphevet--

UPDATE klage.behandling
SET utfall_id                                  = '3',
    dato_behandling_avsluttet_av_saksbehandler = '2024-05-06 13:47:48.499497',
    dato_behandling_avsluttet = '2024-05-05 13:47:48.499497',
    ferdigstilling_nav_ident  = 'H103996',
    ferdigstilling_navn       = 'Teknisk ferdigstilling'
WHERE id = '1881dda6-f78d-480d-a6f9-b0753f42c63c';

-- Sak 5. Medhold i KA, aldri sendt til TR.

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'B100841',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '409a1517-042e-49b2-87d8-e08c0d1cf8d1';


UPDATE klage.behandling
SET utfall_id = '4'
WHERE id = 'b0c54295-85ee-4805-874a-0a20e388d2cc';

-- Sak 6. Ugunst i KA, aldri sendt til TR.

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'G137092',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '9f429970-674d-417d-ae2c-2163fc5da9de';


UPDATE klage.behandling
SET utfall_id = '7'
WHERE id = '7d41add6-6441-4fec-8ab8-ddd02369d039';

-- Sak 7. Besluttet ikke omgjøre, ikke sendt til TR.

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'B169548',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = 'f378275a-a14e-4f49-ae48-544a52c1c532';


UPDATE klage.behandling
SET utfall_id = '16'
WHERE id = '0778eb0f-b5e9-4958-9f6d-ae3949bc8bf1';
