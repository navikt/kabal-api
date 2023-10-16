ALTER TABLE klage.dokument_under_arbeid
    ADD CONSTRAINT fk_dokument_under_arbeid_parent
        FOREIGN KEY (parent_id)
            REFERENCES klage.dokument_under_arbeid (id);