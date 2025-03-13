UPDATE klage.behandling
SET ytelse_id = '52'
WHERE id IN (
             '080530e2-2d38-4d87-b63c-e6d35f91e5a5',
             '7da32580-1093-4c85-9e7c-9e62af395515',
             'de8682ad-c1ae-4f95-99ec-1ec5639b02fc'
    );

UPDATE klage.mottak
SET ytelse_id = '52'
WHERE id IN (
             'd5e99eb0-7b6c-4119-bcc3-30b33d5ecee9',
             '9d8dfc9f-5675-4db3-802a-e891666974d6',
             '0cffaa01-94ea-4532-bbef-4dac39c29c26'
    );