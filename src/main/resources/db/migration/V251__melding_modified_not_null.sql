UPDATE klage.melding
SET modified = created
WHERE modified IS NULL;

ALTER TABLE klage.melding
    ALTER COLUMN modified SET NOT NULL;
