CREATE TABLE klage.rolhistorikk
(
    id            UUID PRIMARY KEY,
    behandling_id UUID NOT NULL,
    rol_ident      TEXT,
    flow_state_id TEXT NOT NULL,
    tidspunkt     TIMESTAMP,
    utfoerende_ident TEXT NOT NULL,
    CONSTRAINT fk_rolhistorikk_behandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.behandling (id)
);

CREATE INDEX behandling_rolhistorikk_idx ON klage.rolhistorikk (behandling_id);