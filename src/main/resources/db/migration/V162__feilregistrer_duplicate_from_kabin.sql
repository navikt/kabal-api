UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Opprettet dobbelt ved en teknisk feil',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
    'ac2b5ce5-9414-416f-ad1d-9f56919a333d'
    );
