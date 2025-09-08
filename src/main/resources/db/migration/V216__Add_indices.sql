CREATE INDEX kafka_event_type_idx ON klage.kafka_event (type);
CREATE INDEX kafka_event_status_idx ON klage.kafka_event (status_id);
CREATE INDEX behandling_forlenget_behandlingstid_draft_idx ON klage.behandling (forlenget_behandlingstid_draft_id);