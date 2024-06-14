CREATE TABLE svarbrev_settings
(
    id                   UUID PRIMARY KEY,
    ytelse_id            TEXT      NOT NULL,
    behandlingstid_weeks INT       NOT NULL,
    custom_text          TEXT,
    should_send          BOOLEAN   NOT NULL,
    created              TIMESTAMP NOT NULL,
    modified             TIMESTAMP NOT NULL,
    created_by           TEXT      NOT NULL
);

CREATE TABLE svarbrev_settings_history
(
    id                   UUID PRIMARY KEY,
    svarbrev_settings_id UUID      NOT NULL REFERENCES svarbrev_settings (id),
    ytelse_id            TEXT      NOT NULL,
    behandlingstid_weeks INT       NOT NULL,
    custom_text          TEXT,
    created              TIMESTAMP NOT NULL,
    created_by           TEXT      NOT NULL,
    should_send          BOOLEAN   NOT NULL
);

--add unique constraint for ytelse_id
CREATE UNIQUE INDEX svarbrev_settings_ytelse_id_uindex
    ON svarbrev_settings (ytelse_id);

--insert svarbrev_settings for all ytelser
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '17', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '10', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '23', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '2', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '1', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '4', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '3', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '5', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '31', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '32', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '7', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '6', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '8', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '22', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '49', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '50', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '26', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '52', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '30', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '20', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '21', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '37', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '36', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '38', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '24', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '19', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '51', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '25', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '35', 12, null, false, now(), now(), 'SYSTEMBRUKER');
INSERT INTO svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, should_send, created, modified, created_by) values (gen_random_uuid(), '44', 12, null, false, now(), now(), 'SYSTEMBRUKER');

insert into svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_weeks, custom_text, created, created_by, should_send)
select gen_random_uuid(), s.id, s.ytelse_id, s.behandlingstid_weeks, s.custom_text, s.modified, s.created_by, s.should_send from svarbrev_settings s;