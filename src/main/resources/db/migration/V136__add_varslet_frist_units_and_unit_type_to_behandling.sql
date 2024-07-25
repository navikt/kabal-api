ALTER TABLE klage.behandling
    ADD COLUMN varslet_behandlingstid_units INT;

ALTER TABLE klage.behandling
    ADD COLUMN varslet_behandlingstid_unit_type_id TEXT;

ALTER TABLE klage.svarbrev_settings_history
    ADD COLUMN behandlingstid_unit_type_id TEXT;

ALTER TABLE klage.svarbrev_settings
    ADD COLUMN behandlingstid_unit_type_id TEXT;


UPDATE klage.svarbrev_settings_history
SET behandlingstid_unit_type_id = 1
WHERE behandlingstid_unit_type = 'WEEKS';

UPDATE klage.svarbrev_settings_history
SET behandlingstid_unit_type_id = 2
WHERE behandlingstid_unit_type = 'MONTHS';

UPDATE klage.svarbrev_settings
SET behandlingstid_unit_type_id = 1
WHERE behandlingstid_unit_type = 'WEEKS';

UPDATE klage.svarbrev_settings
SET behandlingstid_unit_type_id = 2
WHERE behandlingstid_unit_type = 'MONTHS';

-- Drop after testing
-- ALTER TABLE klage.svarbrev_settings
--     DROP COLUMN behandlingstid_unit_type;
--
-- ALTER TABLE klage.svarbrev_settings_history
--     DROP COLUMN behandlingstid_unit_type;

