UPDATE klage.behandling_registreringshjemmel
SET id = 'FTRL_14_17_GAMMEL'
WHERE behandling_id NOT IN (
                          '04373e3a-e8d8-418c-a9ce-b525008bfa8d',
                          '19ea4a3b-94a3-4b9c-af04-fd1bb01d867a',
                          '2bbfe976-8e53-49c2-98d6-d4b693504e70',
                          '8579bb04-63a3-48b4-bdd8-b1093d3e7b97',
                          '9bfbf0b7-60b3-4262-bbf4-fccf718942dd',
                          'b5309602-87c6-4c5a-b9e3-9d81cd0c6720',
                          'b8708de6-cd2b-4ad1-ae34-de136f00929f',
                          'c366fefb-ec37-4f57-9b24-21d24d06e683',
                          'ecbbe95b-b75e-4b5a-92d2-ba9dea6160a5'
    )
  AND id = '429';
