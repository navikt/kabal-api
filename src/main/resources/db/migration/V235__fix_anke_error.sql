-- Meldt fra June. Feilregistrer anke i TR, justerer anke og kjør fullfør-sløyfe på nytt.

UPDATE klage.behandling
SET utfall_id                 = '4',
    dato_behandling_avsluttet = null
WHERE id = '673f4587-ae24-42e4-b028-2fa5a30317b4';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'S148259',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23',
    satt_paa_vent_from            = null,
    satt_paa_vent_to              = null,
    satt_paa_vent_reason          = null,
    satt_paa_vent_reason_id       = null
WHERE id = '0331a425-9016-4bca-8439-0498b3e17726';
