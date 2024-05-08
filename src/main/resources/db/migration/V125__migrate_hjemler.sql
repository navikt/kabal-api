UPDATE klage.behandling_hjemmel h
SET id = 'FTRL_10_7XB'
WHERE h.behandling_id IN (SELECT ih.behandling_id
                          FROM klage.behandling b,
                               klage.behandling_hjemmel ih
                          WHERE ih.behandling_id = b.id
                            AND b.dato_behandling_avsluttet IS NULL
                            AND ih.id = 'FTRL_10_7_3A');

UPDATE klage.behandling_hjemmel h
SET id = 'FTRL_10_7H'
WHERE h.behandling_id IN (SELECT ih.behandling_id
                          FROM klage.behandling b,
                               klage.behandling_hjemmel ih
                          WHERE ih.behandling_id = b.id
                            AND b.dato_behandling_avsluttet IS NULL
                            AND ih.id = 'FTRL_10_7I'
                            AND b.ytelse_id = '49');

DELETE FROM klage.behandling_hjemmel h
WHERE id = 'FTRL_10_7D'
AND behandling_id = '617073fb-cc0b-405e-accb-d7ac56d4b231';

INSERT INTO klage.behandling_hjemmel(id, behandling_id)
VALUES ('0006f295-8c36-4cca-85a0-bb663509c3ac', 'FS_HJE_MM_6D'),
       ('1ce35c47-0353-49a0-b3a4-34f4a3a4294c', 'FS_HJE_MM_6D'),
       ('4117999a-ce2a-4b71-915a-748b3bd12e81', 'FS_HJE_MM_6D'),
       ('41c12220-897a-471e-a9a8-1fdde4b7be4a', 'FS_HJE_MM_6D'),
       ('c3bd5996-173e-4d89-8b9b-1742d8074350', 'FS_HJE_MM_6D');
