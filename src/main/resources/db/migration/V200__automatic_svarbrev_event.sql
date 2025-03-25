CREATE TABLE klage.automatic_svarbrev_event
(
    id                                 UUID PRIMARY KEY NOT NULL,
    created                            TIMESTAMP        NOT NULL,
    status                             TEXT,
    behandling_id                      UUID,
    dokument_under_arbeid_id           UUID,
    receivers_are_set                  BOOLEAN,
    document_is_marked_as_finished     BOOLEAN,
    varslet_frist_is_set_in_behandling BOOLEAN
);