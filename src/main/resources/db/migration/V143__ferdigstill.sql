UPDATE klage.behandling
SET utfall_id = '5',
    dato_behandling_avsluttet = now(),
    dato_behandling_avsluttet_av_saksbehandler = now(),
    kjennelse_mottatt = now(),
    tildelt_saksbehandlerident = 'W109995',
    tildelt_enhet = '4292',
    dato_behandling_tildelt = now()
WHERE id = '42cafb46-8299-4a99-a2bb-c70d69c46b41';