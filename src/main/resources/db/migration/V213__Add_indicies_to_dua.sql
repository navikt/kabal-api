CREATE INDEX dua_parent_idx ON klage.dokument_under_arbeid (parent_id);
CREATE INDEX dua_type_idx ON klage.dokument_under_arbeid (dokument_under_arbeid_type);