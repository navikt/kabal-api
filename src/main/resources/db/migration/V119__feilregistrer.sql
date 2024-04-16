UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Ble ferdigbehandlet utenfor Kabal',
    feilregistrering_fagsystem_id = '23'
WHERE id = '85edc637-ffc4-4486-bb16-11f780a17f1c';