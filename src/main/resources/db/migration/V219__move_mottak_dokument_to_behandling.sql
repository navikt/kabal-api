ALTER TABLE klage.behandling
    ADD COLUMN initiating_system TEXT;

UPDATE klage.behandling b
SET initiating_system = 'KABAL'
WHERE mottak_id IS NULL;

UPDATE klage.behandling b
SET initiating_system = (SELECT sent_from FROM klage.mottak WHERE b.mottak_id = id)
WHERE mottak_id IS NOT NULL;

ALTER TABLE klage.mottak_dokument
    ADD COLUMN behandling_id UUID;

UPDATE klage.mottak_dokument md
SET behandling_id = (SELECT b.id FROM klage.behandling b WHERE b.mottak_id = md.mottak_id);

ALTER TABLE klage.mottak_dokument
    ADD CONSTRAINT fk_mottak_dokument_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id);

ALTER TABLE klage.mottak_dokument
    DROP COLUMN mottak_id;

CREATE INDEX idx_mottak_dokument_behandling_id ON klage.mottak_dokument (behandling_id);

ALTER TABLE klage.behandling
    DROP COLUMN mottak_id;