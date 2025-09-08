CREATE INDEX behandling_saken_gjelder_idx ON klage.behandling (saken_gjelder_value);
CREATE INDEX mu_historikk_behandling_idx ON klage.medunderskriverhistorikk (behandling_id);