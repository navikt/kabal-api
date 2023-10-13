UPDATE klage.dokument_under_arbeid
SET dokument_under_arbeid_type = 'journalfoertdokument'
WHERE journalfoert_dokument_journalpost_id IS NOT NULL
  AND dokument_under_arbeid_type IS NULL;

UPDATE klage.dokument_under_arbeid
SET dokument_under_arbeid_type = 'opplastetdokument'
WHERE mellomlager_id IS NOT NULL
  AND smarteditor_id IS NULL
  AND parent_id IS NULL
  AND dokument_under_arbeid_type IS NULL;

UPDATE klage.dokument_under_arbeid
SET dokument_under_arbeid_type = 'opplastetdokument_vedlegg'
WHERE mellomlager_id IS NOT NULL
  AND smarteditor_id IS NULL
  AND parent_id IS NOT NULL
  AND dokument_under_arbeid_type IS NULL;

UPDATE klage.dokument_under_arbeid
SET dokument_under_arbeid_type = 'smartdokument'
WHERE smarteditor_id IS NOT NULL
  AND parent_id IS NULL
  AND dokument_under_arbeid_type IS NULL;

UPDATE klage.dokument_under_arbeid
SET dokument_under_arbeid_type = 'smartdokument_vedlegg'
WHERE smarteditor_id IS NOT NULL
  AND parent_id IS NOT NULL
  AND dokument_under_arbeid_type IS NULL;

ALTER TABLE klage.dokument_under_arbeid
    ALTER COLUMN dokument_under_arbeid_type SET NOT NULL;