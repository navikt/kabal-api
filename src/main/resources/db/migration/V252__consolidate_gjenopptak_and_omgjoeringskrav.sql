-- Envers stores its own discriminator copy in behandling_aud; update both tables to keep
-- historical entities readable. This irreversibly removes origin information: rollback
-- requires a pre-migration backup of origin. Old binaries are incompatible; coordinate rollout.

UPDATE klage.behandling
SET behandling_type = 'gjenopptak'
WHERE behandling_type IN ('gjenopptak-based-on-journalpost', 'gjenopptak-based-on-kabal-behandling');

UPDATE klage.behandling
SET behandling_type = 'omgjoeringskrav'
WHERE behandling_type IN ('omgjoeringskrav-based-on-journalpost', 'omgjoeringskrav-based-on-kabal-behandling');

UPDATE klage.behandling_aud
SET behandling_type = 'gjenopptak'
WHERE behandling_type IN ('gjenopptak-based-on-journalpost', 'gjenopptak-based-on-kabal-behandling');

UPDATE klage.behandling_aud
SET behandling_type = 'omgjoeringskrav'
WHERE behandling_type IN ('omgjoeringskrav-based-on-journalpost', 'omgjoeringskrav-based-on-kabal-behandling');
