UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'J158513',
    feilregistrering_reason       = 'Ble ferdigbehandlet utenfor Kabal',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
    '567be961-7af5-4db4-99bf-3dd94700142c',
    '8fc9c687-0688-48ae-a753-a56b9a0a915b',
    'f58bfc92-e859-47b2-a819-30878ec998e5'
);