DELETE FROM klage.medunderskriverhistorikk;

ALTER TABLE klage.medunderskriverhistorikk
    ADD COLUMN medunderskriver_flow_state_id TEXT NOT NULL DEFAULT 'cannot happen',
    ADD COLUMN utfoerende_ident TEXT NOT NULL DEFAULT 'cannot happen';
