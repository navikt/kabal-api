ALTER TABLE klage.svarbrev_settings
    ADD COLUMN behandlingstid_unit_type TEXT DEFAULT 'WEEKS' NOT NULL;

ALTER TABLE klage.svarbrev_settings_history
    ADD COLUMN behandlingstid_unit_type TEXT DEFAULT 'WEEKS' NOT NULL;

ALTER TABLE klage.svarbrev_settings
    RENAME COLUMN behandlingstid_weeks TO behandlingstid_units;

ALTER TABLE klage.svarbrev_settings_history
    RENAME COLUMN behandlingstid_weeks TO behandlingstid_units;