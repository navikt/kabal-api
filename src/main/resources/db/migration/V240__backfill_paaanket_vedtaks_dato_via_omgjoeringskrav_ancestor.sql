-- Follow-up backfill of paaanket_vedtaks_dato.
--
-- V238 and V239 only treated 'klage' as the originating ancestor. An anke/gjenopptak can also be
-- based on an omgjoeringskrav, so behandlinger descending from an omgjoeringskrav were left without
-- a value. Here we walk the previous_behandling_id chain backwards for each candidate behandling
-- until we reach the nearest omgjoeringskrav ancestor, and set paaanket_vedtaks_dato from that
-- omgjoeringskrav's dato_behandling_avsluttet_av_saksbehandler.
--
-- This script only touches behandlinger that are still missing a value, so it is safe to run after
-- V238 and V239.
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

    -- Walk one step further back, but stop once we hit an omgjoeringskrav (or run out of ancestors).
    -- The depth guard protects against unexpected cycles in previous_behandling_id.
    SELECT c.behandling_id,
           n.previous_behandling_id,
           c.depth + 1
    FROM chain c
             JOIN klage.behandling n ON n.id = c.node_id
    WHERE n.behandling_type NOT LIKE 'omgjoeringskrav%'
      AND n.previous_behandling_id IS NOT NULL
      AND c.depth < 100
)
UPDATE klage.behandling target
SET paaanket_vedtaks_dato = omgjoeringskrav.dato_behandling_avsluttet_av_saksbehandler::date
FROM chain c
         JOIN klage.behandling omgjoeringskrav ON omgjoeringskrav.id = c.node_id
WHERE target.id = c.behandling_id
  AND omgjoeringskrav.behandling_type LIKE 'omgjoeringskrav%'
  AND omgjoeringskrav.dato_behandling_avsluttet_av_saksbehandler IS NOT NULL
  AND target.paaanket_vedtaks_dato IS NULL;
