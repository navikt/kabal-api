CREATE TABLE klage.task_list_merkantil
(
    id              UUID PRIMARY KEY,
    behandling_id   UUID      NOT NULL references klage.behandling (id),
    reason          TEXT      NOT NULL,
    created         TIMESTAMP NOT NULL,
    date_handled    TIMESTAMP,
    handled_by      TEXT,
    handled_by_name TEXT,
    comment         TEXT
);