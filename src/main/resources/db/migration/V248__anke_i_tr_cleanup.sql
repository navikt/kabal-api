--Feilregistreres

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'H154142',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = 'b398d2f4-6cd8-409e-80f1-5e199124e33c';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'S157894',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = '69c9b025-c2c9-4ffa-ba24-80bca787cfac';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'H143404',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = '8f35480d-6885-4275-8a8a-c1153eedcae2';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'T109464',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = 'b87f0d9e-0ca0-4596-8cf2-0f3063743b33';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'B164340',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = '1427ffa3-099b-4cb1-98a7-d1e13563c10a';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'W161655',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = '31da97cc-9e3e-402a-8c4a-e05b4434ee17';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'S167029',
    feilregistrering_registered   = now(),
    feilregistrering_reason       = 'Teknisk feilregistrering, ble aldri sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id = 'd6196d1b-974e-4035-8205-41a3ff055a09';

--Ferdigstilles

UPDATE klage.behandling
SET utfall_id                                  = '6',
    kjennelse_mottatt = '2025-07-01 13:47:48.499497',
    dato_behandling_avsluttet_av_saksbehandler = '2025-07-01 15:47:48.499497',
    dato_behandling_avsluttet = '2025-07-01 15:49:48.499497',
    ferdigstilling_nav_ident  = 'T109464',
    ferdigstilling_navn       = 'Teknisk ferdigstilling'
WHERE id = 'b87f0d9e-0ca0-4596-8cf2-0f3063743b33';
