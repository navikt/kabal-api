UPDATE klage.behandling
SET feilregistrering_registered   = '2023-03-06 13:30:13.000000',
    feilregistrering_nav_ident    = 'SYSTEM',
    feilregistrering_reason       = 'Opprettet dobbelt ved en teknisk feil',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
             'f0206c0c-3fa7-4bc4-bad2-a0dfbc4b1f3a'
    );
