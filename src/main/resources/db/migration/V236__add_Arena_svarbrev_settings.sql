-- Tilleggsstønad arbeidssøkere
INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '56', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '1', '1');

INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '56', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '2', '1');

INSERT INTO klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_units, custom_text,
                                             created, created_by, should_send, behandlingstid_unit_type, type_id,
                                             behandlingstid_unit_type_id)
SELECT gen_random_uuid(),
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
FROM klage.svarbrev_settings s
WHERE type_id IN ('1', '2')
  AND ytelse_id = '56';

-- Tiltakspenger
INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '33', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '1', '1');

INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '33', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '2', '1');

INSERT INTO klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_units, custom_text,
                                             created, created_by, should_send, behandlingstid_unit_type, type_id,
                                             behandlingstid_unit_type_id)
SELECT gen_random_uuid(),
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
FROM klage.svarbrev_settings s
WHERE type_id IN ('1', '2')
  AND ytelse_id = '33';

-- Oppfølgingssak - Tiltaksplass
INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '34', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '1', '1');

INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '34', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '2', '1');

INSERT INTO klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_units, custom_text,
                                             created, created_by, should_send, behandlingstid_unit_type, type_id,
                                             behandlingstid_unit_type_id)
SELECT gen_random_uuid(),
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
FROM klage.svarbrev_settings s
WHERE type_id IN ('1', '2')
  AND ytelse_id = '34';

-- Oppfølgingssak - NAV-loven §14a
INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '40', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '1', '1');

INSERT INTO klage.svarbrev_settings (id, ytelse_id, behandlingstid_units, custom_text, should_send, created, modified,
                                     created_by, behandlingstid_unit_type, type_id, behandlingstid_unit_type_id)
VALUES (gen_random_uuid(), '40', 12, null, false, now(), now(), 'SYSTEMBRUKER', 'WEEKS', '2', '1');

INSERT INTO klage.svarbrev_settings_history (id, svarbrev_settings_id, ytelse_id, behandlingstid_units, custom_text,
                                             created, created_by, should_send, behandlingstid_unit_type, type_id,
                                             behandlingstid_unit_type_id)
SELECT gen_random_uuid(),
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
FROM klage.svarbrev_settings s
WHERE type_id IN ('1', '2')
  AND ytelse_id = '40';