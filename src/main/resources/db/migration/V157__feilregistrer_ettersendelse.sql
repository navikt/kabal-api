UPDATE klage.behandling
SET feilregistrering_registered   = now(),
    feilregistrering_nav_ident    = 'W132204',
    feilregistrering_reason       = 'Skulle egentlig være en ettersendelse',
    feilregistrering_fagsystem_id = '1'
WHERE id in (
             '4c104c49-c14e-4648-8da6-d5d122c2e4c0'
    );
