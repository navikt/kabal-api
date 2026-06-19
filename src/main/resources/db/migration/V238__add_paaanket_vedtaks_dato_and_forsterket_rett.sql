ALTER TABLE klage.behandling
    ADD COLUMN paaanket_vedtaks_dato DATE,
    ADD COLUMN forsterket_rett       BOOLEAN;

ALTER TABLE klage.behandling_aud
    ADD COLUMN paaanket_vedtaks_dato DATE,
    ADD COLUMN forsterket_rett       BOOLEAN;

-- Backfill paaanket_vedtaks_dato for trygderetten-relevant behandlinger that have already been
-- ferdigstilt, using the date the saksbehandler completed the behandling.
UPDATE klage.behandling
SET paaanket_vedtaks_dato = dato_behandling_avsluttet_av_saksbehandler::date
WHERE paaanket_vedtaks_dato IS NULL
  AND dato_behandling_avsluttet_av_saksbehandler IS NOT NULL
  AND behandling_type IN (
                          'anke',
                          'anke_i_trygderetten',
                          'gjenopptak_i_trygderetten',
                          'gjenopptak-based-on-journalpost',
                          'gjenopptak-based-on-kabal-behandling'
    );
