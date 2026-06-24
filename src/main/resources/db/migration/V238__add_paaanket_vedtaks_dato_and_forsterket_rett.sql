ALTER TABLE klage.behandling
    ADD COLUMN paaanket_vedtaks_dato DATE,
    ADD COLUMN forsterket_rett       BOOLEAN;

ALTER TABLE klage.behandling_aud
    ADD COLUMN paaanket_vedtaks_dato DATE,
    ADD COLUMN forsterket_rett       BOOLEAN;

-- Backfill paaanket_vedtaks_dato for trygderetten-relevant behandlinger.
--
-- The value originates from the klagebehandling (the behandling the anke/gjenopptak was based on),
-- using the date the saksbehandler completed that klagebehandling
-- (dato_behandling_avsluttet_av_saksbehandler). It is then copied unchanged down the chain
-- (e.g. anke -> anke_i_trygderetten, gjenopptak -> gjenopptak_i_trygderetten), so for any
-- behandling we take its previous behandling's already resolved paaanket_vedtaks_dato when present,
-- and otherwise fall back to the previous behandling's avsluttet-date ONLY when that previous
-- behandling is a klagebehandling.
DO
$$
    DECLARE
        updated_rows INTEGER;
    BEGIN
        LOOP
            UPDATE klage.behandling b
            SET paaanket_vedtaks_dato =
                    COALESCE(prev.paaanket_vedtaks_dato,
                             CASE
                                 WHEN prev.behandling_type = 'klage'
                                     THEN prev.dato_behandling_avsluttet_av_saksbehandler::date
                                 END)
            FROM klage.behandling prev
            WHERE b.previous_behandling_id = prev.id
              AND b.paaanket_vedtaks_dato IS NULL
              AND COALESCE(prev.paaanket_vedtaks_dato,
                           CASE
                               WHEN prev.behandling_type = 'klage'
                                   THEN prev.dato_behandling_avsluttet_av_saksbehandler::date
                               END) IS NOT NULL
              AND b.behandling_type IN (
                                        'anke',
                                        'anke_i_trygderetten',
                                        'gjenopptak_i_trygderetten',
                                        'gjenopptak-based-on-journalpost',
                                        'gjenopptak-based-on-kabal-behandling'
                  );

            GET DIAGNOSTICS updated_rows = ROW_COUNT;
            EXIT WHEN updated_rows = 0;
        END LOOP;
    END
$$;
