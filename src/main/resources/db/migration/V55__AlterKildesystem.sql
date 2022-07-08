ALTER TABLE klage.mottak
DROP
COLUMN kildesystem;

ALTER TABLE klage.mottak
    ALTER COLUMN sak_fagsystem SET NOT NULL;

ALTER TABLE klage.behandling
DROP
COLUMN kildesystem;

ALTER TABLE klage.behandling
    ALTER COLUMN sak_fagsystem SET NOT NULL;