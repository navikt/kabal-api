ALTER TABLE klage.behandling
    ADD COLUMN gosys_oppgave_required BOOLEAN DEFAULT false;

UPDATE klage.behandling
SET gosys_oppgave_required = true
WHERE gosys_oppgave_id IS NOT NULL;

UPDATE klage.behandling
SET gosys_oppgave_required = true
WHERE source_behandling_id IN
      (SELECT id FROM klage.behandling WHERE behandling_type = 'omgjoeringskrav-based-on-journalpost');