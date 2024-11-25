INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
values (gen_random_uuid(), '27', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '1', '1');

insert into klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_units, custom_text,
                                             created, created_by, should_send, behandlingstid_unit_type, type_id,
                                             behandlingstid_unit_type_id)
select gen_random_uuid(),
       s.id,
       s.ytelse_id,
       s.behandlingstid_units,
       s.custom_text,
       s.modified,
       s.created_by,
       s.should_send,
       s.behandlingstid_unit_type,
       s.type_id,
       s.behandlingstid_unit_type_id
from klage.svarbrev_settings s
WHERE type_id = '1'
  AND ytelse_id = '27';

INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
values (gen_random_uuid(), '27', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '2', '1');

select gen_random_uuid(),
       s.id,
       s.ytelse_id,
       s.behandlingstid_units,
       s.custom_text,
       s.modified,
       s.created_by,
       s.should_send,
       s.behandlingstid_unit_type,
       s.type_id,
       s.behandlingstid_unit_type_id
from klage.svarbrev_settings s
WHERE type_id = '2'
  AND ytelse_id = '27';