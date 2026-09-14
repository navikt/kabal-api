ALTER TABLE klage.behandling
    ADD COLUMN IF NOT EXISTS trygderetten_saksnummer TEXT;

ALTER TABLE klage.behandling_aud
    ADD COLUMN IF NOT EXISTS trygderetten_saksnummer TEXT;
