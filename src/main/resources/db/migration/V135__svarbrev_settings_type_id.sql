ALTER TABLE klage.svarbrev_settings
    ADD COLUMN type_id TEXT DEFAULT '1' NOT NULL;

ALTER TABLE klage.svarbrev_settings_history
    ADD COLUMN type_id TEXT DEFAULT '1' NOT NULL;

--fjern index
ALTER TABLE klage.svarbrev_settings
    DROP CONSTRAINT svarbrev_settings_ytelse_id_uindex;

--lag ny index
CREATE UNIQUE INDEX svarbrev_settings_ytelse_id_type_id_uindex
    ON klage.svarbrev_settings (ytelse_id, type_id);

--legg til default for anker
insert into klage.svarbrev_settings (id, ytelse_id, behandlingstid_weeks, custom_text, created, created_by, should_send, modified, type_id)
select gen_random_uuid(), s.ytelse_id, s.behandlingstid_weeks, s.custom_text, s.modified, s.created_by, s.should_send, s.modified, '2' from klage.svarbrev_settings s;

DELETE FROM klage.svarbrev_settings_history;

insert into klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_weeks, custom_text, created, created_by, should_send, type_id)
select gen_random_uuid(), s.id, s.ytelse_id, s.behandlingstid_weeks, s.custom_text, s.modified, s.created_by, s.should_send, s.type_id from klage.svarbrev_settings s;