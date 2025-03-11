ALTER TABLE klage.forlenget_behandlingstid_draft
    ADD COLUMN begrunnelse TEXT,
    ADD COLUMN do_not_send_letter BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE klage.behandling
    ADD COLUMN varslet_begrunnelse TEXT,
    ADD COLUMN varslet_varsel_type TEXT DEFAULT 'OPPRINNELIG',
    ADD COLUMN varslet_do_not_send_letter BOOLEAN DEFAULT FALSE;

ALTER TABLE klage.behandling_aud
    ADD COLUMN varslet_begrunnelse TEXT,
    ADD COLUMN varslet_varsel_type TEXT,
    ADD COLUMN varslet_do_not_send_letter BOOLEAN DEFAULT FALSE;

ALTER TABLE klage.varslet_behandlingstid_historikk
    ADD COLUMN varslet_begrunnelse TEXT,
    ADD COLUMN varslet_varsel_type TEXT DEFAULT 'OPPRINNELIG',
    ADD COLUMN varslet_do_not_send_letter BOOLEAN DEFAULT FALSE;