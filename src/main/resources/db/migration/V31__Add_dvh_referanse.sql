ALTER TABLE klage.klagebehandling
    ADD COLUMN dvh_referanse TEXT;

-- taken from https://stackoverflow.com/questions/7869592/how-to-do-an-update-join-in-postgresql
UPDATE klage.klagebehandling AS k
SET dvh_referanse = m.dvh_referanse
FROM klage.mottak AS m
WHERE k.mottak_id = m.id