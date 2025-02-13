UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Skulle vært BETONG',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
    '06dda241-4bea-4552-adc3-1739e7366230',
    'ce38033b-8f07-492c-bd29-ad3048120607'
    );

update klage.behandling
SET dato_behandling_avsluttet_av_saksbehandler = null,
    dato_behandling_avsluttet = null,
    utfall_id = null
WHERE id in (
    '5cf42920-e3e3-467a-9a08-ca6efbfb272a',
    '6856aa41-aabd-430e-8583-dbf7b2548a42'
    );