DELETE FROM klage.medunderskriverhistorikk;

ALTER TABLE klage.medunderskriverhistorikk
    ADD COLUMN flow_state_id TEXT NOT NULL,
    ADD COLUMN utfoerende_ident TEXT NOT NULL;
