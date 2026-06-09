UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4A'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7A'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4B'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7B'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4C'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7C'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4D'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7D'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4E'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7E'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4F'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7F'
  AND b.kaka_kvalitetsvurdering_version <> 1;

UPDATE klage.behandling_registreringshjemmel h
SET id = 'FTRL_14_4G'
FROM klage.behandling b
WHERE h.behandling_id = b.id
  AND b.ytelse_id = '8'
  AND h.id = 'FTRL_14_7I'
  AND b.kaka_kvalitetsvurdering_version <> 1;