ALTER TABLE klage.behandling
    ADD COLUMN trygderetten_saksnummer TEXT;

ALTER TABLE klage.behandling_aud
    ADD COLUMN trygderetten_saksnummer TEXT;
