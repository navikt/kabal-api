-- Uploaded documents are uploaded directly to Google by the client, and virus scanned and converted
-- to PDF by kabal-file-api. These columns track that process.
ALTER TABLE klage.dokument_under_arbeid
    ADD COLUMN status VARCHAR(30),
    ADD COLUMN scanned_generation BIGINT;

ALTER TABLE klage.dokument_under_arbeid_aud
    ADD COLUMN status VARCHAR(30),
    ADD COLUMN scanned_generation BIGINT;

-- Everything that already exists has been through the old (in kabal-api) scan and conversion.
UPDATE klage.dokument_under_arbeid
SET status = 'DONE'
WHERE dokument_under_arbeid_type IN ('opplastetdokument', 'opplastetdokument_vedlegg');

UPDATE klage.dokument_under_arbeid_aud
SET status = 'DONE'
WHERE dokument_under_arbeid_type IN ('opplastetdokument', 'opplastetdokument_vedlegg');
