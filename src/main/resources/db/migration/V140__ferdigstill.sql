UPDATE klage.behandling
SET utfall_id = '6',
    dato_behandling_avsluttet = now(),
    dato_behandling_avsluttet_av_saksbehandler = now(),
    kjennelse_mottatt = now(),
    tildelt_saksbehandlerident = 'W109995',
    tildelt_enhet = '4292',
    dato_behandling_tildelt = now()
WHERE id = '7f5449fa-5c77-4c68-bdf2-18181fcd2aca';