ALTER TABLE klage.forlenget_behandlingstid_draft
    ADD COLUMN begrunnelse TEXT;

ALTER TABLE klage.behandling
    ADD COLUMN begrunnelse TEXT,
    ADD COLUMN varsel_type TEXT DEFAULT 'OPPRINNELIG' NOT NULL;

ALTER TABLE klage.behandling_aud
    ADD COLUMN begrunnelse TEXT,
    ADD COLUMN varsel_type TEXT;

ALTER TABLE klage.varslet_behandlingstid_historikk
    ADD COLUMN begrunnelse TEXT,
    ADD COLUMN varsel_type TEXT DEFAULT 'OPPRINNELIG';