--mottak
UPDATE klage.mottak
SET dvh_referanse = '48428906'
WHERE kilde_referanse = '65078640'
  AND dvh_referanse = '46577693';
UPDATE klage.mottak
SET dvh_referanse = '49052740'
WHERE kilde_referanse = '65693125'
  AND dvh_referanse = '47286111';
UPDATE klage.mottak
SET dvh_referanse = '49188049'
WHERE kilde_referanse = '66219670'
  AND dvh_referanse = '48339550';

-- behandling
UPDATE klage.behandling
SET dvh_referanse = '48428906'
WHERE kilde_referanse = '65078640'
  AND dvh_referanse = '46577693';
UPDATE klage.behandling
SET dvh_referanse = '49052740'
WHERE kilde_referanse = '65693125'
  AND dvh_referanse = '47286111';
UPDATE klage.behandling
SET dvh_referanse = '49188049'
WHERE kilde_referanse = '66219670'
  AND dvh_referanse = '48339550';
