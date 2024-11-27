UPDATE klage.behandling_registreringshjemmel
SET id = 'FTRL_17_5_1'
WHERE behandling_id = '48204b5a-b740-457e-ae63-feca7112ac98'
  AND id = '555';

UPDATE klage.behandling
SET ytelse_id = '52'
WHERE id IN (
             '9fa978cd-155e-473b-94be-6865f5add5d5',
             '1651c697-e72e-42a9-a70b-759bf06fc531',
             '48204b5a-b740-457e-ae63-feca7112ac98',
             'ea6dbaf5-e58b-4fb2-98c4-9e226ed6c7dc',
             '31327f3b-7b29-4938-8b5b-e899034d0622',
             'e653871f-41cd-46b6-81ee-60c3da04b752'
    );