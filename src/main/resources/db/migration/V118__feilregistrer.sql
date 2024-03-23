UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Ble ferdigbehandlet utenfor Kabal',
    feilregistrering_fagsystem_id = '23'
WHERE id = '91c5dda4-0166-4e5e-82b9-c2c6da0fc416';