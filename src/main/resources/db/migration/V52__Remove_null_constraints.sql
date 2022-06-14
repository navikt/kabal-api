ALTER TABLE klage.dokument_under_arbeid
    ALTER COLUMN mellomlager_id DROP NOT NULL,
    ALTER COLUMN opplastet DROP NOT NULL,
    ALTER COLUMN size DROP NOT NULL;