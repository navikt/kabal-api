ALTER TABLE klage.behandling
    ADD COLUMN initiating_system TEXT;

UPDATE klage.behandling b
SET initiating_system = 'KABAL'
WHERE mottak_id IS NULL;

UPDATE klage.behandling b
SET initiating_system = (SELECT sent_from FROM klage.mottak WHERE b.mottak_id = id)
WHERE mottak_id IS NOT NULL;

ALTER TABLE klage.behandling
    ALTER COLUMN initiating_system SET NOT NULL;

ALTER TABLE klage.mottak_dokument
    ADD COLUMN behandling_id UUID REFERENCES klage.behandling(id);

UPDATE klage.mottak_dokument md
SET behandling_id = (SELECT b.id FROM klage.behandling b WHERE b.mottak_id = md.mottak_id);

CREATE INDEX idx_mottak_dokument_behandling_id ON klage.mottak_dokument (behandling_id);

ALTER TABLE klage.mottak_dokument
    ALTER COLUMN behandling_id SET NOT NULL;

ALTER TABLE klage.mottak_dokument
    ALTER COLUMN mottak_id DROP NOT NULL;

-- Do after confirmed in prod
-- ALTER TABLE klage.mottak_dokument
--     DROP COLUMN mottak_id;
--
-- ALTER TABLE klage.behandling
--     DROP COLUMN mottak_id;