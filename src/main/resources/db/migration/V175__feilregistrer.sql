UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Saken henlagt. Ikke mulig å feilregistrere via Kabal, da saken ble avsluttet manuelt i Infotrygd.',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
    '30d23d22-f937-4b11-8cfd-ed83c0067a37'
    );