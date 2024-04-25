ALTER TABLE klage.dokument_under_arbeid
    ADD COLUMN language TEXT;

UPDATE klage.dokument_under_arbeid
SET language = 'NB'
WHERE smarteditor_id IS NOT NULL;