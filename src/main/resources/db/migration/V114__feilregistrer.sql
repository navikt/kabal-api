UPDATE klage.behandling
SET feilregistrering_registered   = '2023-03-05 13:30:13.000000',
    feilregistrering_nav_ident    = 'SYSTEM',
    feilregistrering_reason       = 'Ble ferdigbehandlet utenfor Kabal',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
             'ad343f39-1c0e-4d52-b62f-2340bf069174'
    );
