ALTER TABLE klage.mottak_dokument
    ADD COLUMN behandling_id UUID REFERENCES klage.behandling (id);

ALTER TABLE klage.behandling
    ADD COLUMN sent_from TEXT;

UPDATE klage.mottak_dokument md
SET behandling_id = (SELECT b.id FROM klage.behandling b WHERE b.mottak_id = md.mottak_id);

UPDATE klage.behandling b
SET sent_from = (SELECT m.sent_from FROM klage.mottak m WHERE b.mottak_id = m.id);

ALTER TABLE klage.mottak_dokument
    DROP COLUMN mottak_id;

CREATE INDEX idx_mottak_dokument_behandling_id ON klage.mottak_dokument (behandling_id);

ALTER TABLE klage.behandling
    DROP COLUMN mottak_id;