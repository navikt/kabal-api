CREATE INDEX fk_dua_brevmottaker_ix ON klage.brevmottaker (dokument_under_arbeid_id);
CREATE INDEX fk_dua_dokarkiv_reference_ix ON klage.dokument_under_arbeid_dokarkiv_reference (dokument_under_arbeid_id);
CREATE INDEX fk_behandling_dua_ix ON klage.dokument_under_arbeid (behandling_id);