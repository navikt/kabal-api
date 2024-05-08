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
