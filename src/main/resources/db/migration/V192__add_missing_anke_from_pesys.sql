UPDATE klage.behandling
SET dvh_referanse = '48270708'
WHERE kilde_referanse = '51465082'
  AND dvh_referanse = '36263910';

UPDATE klage.behandling
SET dato_mottatt_klageinstans = '2024-02-07 00:00:00.000000'
WHERE kilde_referanse = '51465082'
  AND dvh_referanse = '48270708';