UPDATE klage.behandling
SET utfall_id = '6',
    dato_behandling_avsluttet = now(),
    dato_behandling_avsluttet_av_saksbehandler = now(),
    kjennelse_mottatt = now(),
    tildelt_saksbehandlerident = 'F128901',
    tildelt_enhet = '4293',
    dato_behandling_tildelt = now()
WHERE id = 'd2a51401-90fa-434d-abc9-5313f2dc250f';