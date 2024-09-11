ALTER TABLE klage.behandling
    ADD COLUMN oppgave_returned_tildelt_enhetsnummer TEXT,
    ADD COLUMN oppgave_returned_mappe_id BIGINT,
    ADD COLUMN oppgave_returned_kommentar TEXT;
