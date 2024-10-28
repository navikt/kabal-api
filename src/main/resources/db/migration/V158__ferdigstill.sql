UPDATE klage.behandling
SET utfall_id = '6',
    dato_behandling_avsluttet = now(),
    dato_behandling_avsluttet_av_saksbehandler = now(),
    kjennelse_mottatt = '2024-10-23',
    tildelt_saksbehandlerident = 'S160846',
    tildelt_enhet = '4295',
    dato_behandling_tildelt = now()
WHERE id = '8207335c-b1c6-495a-bb1c-8a3fe3e45857';