ALTER TABLE klage.behandling
    ADD COLUMN avslutt_gosys_oppgave BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN avslutt_gosys_oppgave_kommentar TEXT;

ALTER TABLE klage.behandling_aud
    ADD COLUMN avslutt_gosys_oppgave BOOLEAN,
    ADD COLUMN avslutt_gosys_oppgave_kommentar TEXT;
