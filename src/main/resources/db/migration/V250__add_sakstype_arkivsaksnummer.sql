ALTER TABLE klage.behandling ADD COLUMN sak_sakstype_id TEXT NOT NULL DEFAULT '1';
ALTER TABLE klage.behandling ADD COLUMN sak_arkivsaksnummer TEXT;
ALTER TABLE klage.behandling ALTER COLUMN sak_fagsak_id DROP NOT NULL;

ALTER TABLE klage.behandling_aud ADD COLUMN sak_sakstype_id TEXT;
ALTER TABLE klage.behandling_aud ADD COLUMN sak_arkivsaksnummer TEXT;
