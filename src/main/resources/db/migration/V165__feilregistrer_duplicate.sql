UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Opprettet dobbelt ved en teknisk feil',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
    '0bb5b6f8-ef9f-43db-ba86-bc59bffedf20'
    );