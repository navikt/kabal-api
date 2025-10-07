ALTER TABLE klage.behandling
    ADD COLUMN previous_behandling_id UUID REFERENCES klage.behandling(id);