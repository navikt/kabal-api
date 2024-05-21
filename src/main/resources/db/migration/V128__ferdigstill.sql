UPDATE klage.behandling
SET utfall_id = '6',
    dato_behandling_avsluttet = now(),
    dato_behandling_avsluttet_av_saksbehandler = now(),
    kjennelse_mottatt = now(),
    tildelt_saksbehandlerident = 'F128901'
WHERE id = 'a6304bf6-6c63-4c1c-a2c2-022939df713a';