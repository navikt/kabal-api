-- Åpne opp anke-i-tr-behandlinger slik at de kan behandles på nytt.

UPDATE klage.behandling
SET dato_behandling_avsluttet_av_saksbehandler = NULL,
    dato_behandling_avsluttet                  = NULL,
    ferdigstilling_nav_ident                   = NULL,
    ferdigstilling_navn                        = NULL,
    ny_ankebehandling_ka                       = NULL,
    modified                                   = NOW()
WHERE id = '3cf9be26-9d57-4792-a10d-37221748cd39';

UPDATE klage.behandling
SET dato_behandling_avsluttet_av_saksbehandler = NULL,
    dato_behandling_avsluttet                  = NULL,
    ferdigstilling_nav_ident                   = NULL,
    ferdigstilling_navn                        = NULL,
    ny_ankebehandling_ka                       = NULL,
    modified                                   = NOW()
WHERE id = '596f235c-1beb-4036-afd2-5d6872686e32';

-- Fjern ankebehandlinger som ikke skulle blitt laget av sbh.

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'J158513',
    feilregistrering_registered   = NOW(),
    feilregistrering_reason       = 'Teknisk feilregistrering. Skulle ikke blitt opprettet slik av sbh.',
    feilregistrering_fagsystem_id = '23',
    modified                      = NOW()
WHERE id = '29b8bd04-37e1-4d0e-9842-2917304dc06a';

UPDATE klage.behandling
SET feilregistrering_nav_ident    = 'J158513',
    feilregistrering_registered   = NOW(),
    feilregistrering_reason       = 'Teknisk feilregistrering. Skulle ikke blitt opprettet slik av sbh.',
    feilregistrering_fagsystem_id = '23',
    modified                      = NOW()
WHERE id = 'c77252be-e805-41b1-83d7-32e66699e39c';
