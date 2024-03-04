UPDATE klage.behandling
SET feilregistrering_registered   = '2023-03-04 14:56:13.000000',
    feilregistrering_nav_ident    = 'SYSTEM',
    feilregistrering_reason       = 'Ble ferdigbehandlet utenfor Kabal',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
             '6403e2d1-d88e-4e57-841c-0be0a00c398e',
             '70ad552f-8f37-4905-8acb-777cf45d9fc9'
    );
