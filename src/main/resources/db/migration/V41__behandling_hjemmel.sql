ALTER TABLE klage.klagebehandling_hjemmel
    RENAME TO behandling_hjemmel;

ALTER TABLE klage.behandling_hjemmel
    RENAME COLUMN klagebehandling_id TO behandling_id;

ALTER TABLE klage.behandling_hjemmel
    ADD CONSTRAINT fk_hjemmel_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id);

ALTER TABLE klage.behandling_hjemmel
    DROP CONSTRAINT fk_hjemmel_klagebehandling;