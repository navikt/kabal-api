-- Follow-up backfill of paaanket_vedtaks_dato.
--
-- V238 backfilled the value one hop at a time, which left some behandlinger empty when an
-- intermediate behandling in the chain (e.g. an anke_i_trygderetten) was not resolved. Here we
-- instead walk the previous_behandling_id chain backwards for each candidate behandling until we
-- reach the nearest klagebehandling ancestor, and set paaanket_vedtaks_dato from that klage's
-- dato_behandling_avsluttet_av_saksbehandler.
--
-- Chains such as klage -> anke -> anke_i_trygderetten -> anke -> ... are common, so a behandling is
-- not necessarily a direct child of the klagebehandling. This script only touches behandlinger that
-- are still missing a value, so it is safe to run after V238.
WITH RECURSIVE chain AS (
    -- Seed: each candidate behandling that still needs a value, pointing at its immediate ancestor.
    SELECT b.id                     AS behandling_id,
           b.previous_behandling_id AS node_id,
           1                        AS depth
    FROM klage.behandling b
    WHERE b.paaanket_vedtaks_dato IS NULL
      AND b.previous_behandling_id IS NOT NULL
      AND b.behandling_type IN (
                                'anke',
                                'anke_i_trygderetten',
                                'gjenopptak_i_trygderetten',
                                'gjenopptak-based-on-journalpost',
                                'gjenopptak-based-on-kabal-behandling'
          )

    UNION ALL

    -- Walk one step further back, but stop once we hit a klagebehandling (or run out of ancestors).
    -- The depth guard protects against unexpected cycles in previous_behandling_id.
    SELECT c.behandling_id,
           n.previous_behandling_id,
           c.depth + 1
    FROM chain c
             JOIN klage.behandling n ON n.id = c.node_id
    WHERE n.behandling_type <> 'klage'
      AND n.previous_behandling_id IS NOT NULL
      AND c.depth < 100
)
UPDATE klage.behandling target
SET paaanket_vedtaks_dato = klage.dato_behandling_avsluttet_av_saksbehandler::date
FROM chain c
         JOIN klage.behandling klage ON klage.id = c.node_id
WHERE target.id = c.behandling_id
  AND klage.behandling_type = 'klage'
  AND klage.dato_behandling_avsluttet_av_saksbehandler IS NOT NULL
  AND target.paaanket_vedtaks_dato IS NULL;
