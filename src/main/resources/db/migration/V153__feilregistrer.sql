UPDATE klage.behandling
SET feilregistrering_registered   = '2024-10-14 14:00:13.000000',
    feilregistrering_nav_ident    = 'W132204',
    feilregistrering_reason       = 'Har ikke blitt sendt til TR',
    feilregistrering_fagsystem_id = '23'
WHERE id in (
             '0ecd6ab0-1576-4e51-b289-edb923869ad3',
             '22791ca2-7bc5-432b-ae72-fe79274c514a',
             'b22f84a7-d1ce-4ad8-a3b2-b3d4d9747694',
             '8ff99ab4-012b-45f7-96d6-fc909704cecb',
             'f5110637-2fee-4187-a27c-b2dd2a4a3b27',
             'bf1b5a72-fb5b-4a07-bac7-0b1a94d6afac',
             '6ef6b71a-8649-4db5-a82b-50891456a841',
             'b3c1a12b-8d9a-449e-a5eb-8374c7ed693f',
             '91bb2191-db34-4dfb-b720-989e1157b115',
             '7ff2e138-946a-427a-b957-ccbd25a8e8ea'
    );
