ALTER TABLE klage.behandling
    ADD COLUMN ignore_gosys_oppgave BOOLEAN default false;

ALTER TABLE klage.behandling
    RENAME COLUMN oppgave_id TO gosys_oppgave_id;