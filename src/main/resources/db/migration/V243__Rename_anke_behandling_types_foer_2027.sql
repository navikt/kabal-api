-- Anke and AnkeITrygderetten are split into foer2027 and etter2027 variants to match new
-- types in kodeverk. Existing rows are all of the foer2027 variant.
-- The etter2027 discriminators ('anke_etter_2027', 'anke_i_trygderetten_etter_2027') need no
-- migration: SINGLE_TABLE inheritance means all columns already exist, and no rows use them yet.

UPDATE klage.behandling
SET behandling_type = 'anke_foer_2027'
WHERE behandling_type = 'anke';

UPDATE klage.behandling
SET behandling_type = 'anke_i_trygderetten_foer_2027'
WHERE behandling_type = 'anke_i_trygderetten';

-- Envers keeps its own copy of the discriminator. Without this the revision history
-- becomes unreadable for the renamed entities.
UPDATE klage.behandling_aud
SET behandling_type = 'anke_foer_2027'
WHERE behandling_type = 'anke';

UPDATE klage.behandling_aud
SET behandling_type = 'anke_i_trygderetten_foer_2027'
WHERE behandling_type = 'anke_i_trygderetten';
