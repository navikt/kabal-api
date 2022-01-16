--Rename vedtak to delbehandling
ALTER TABLE klage.vedtak
    RENAME TO delbehandling;

--Reuse old FK field for new FK
ALTER TABLE klage.delbehandling
    RENAME COLUMN klagebehandling_id TO behandling_id;

--Move link from parent to child
UPDATE klage.delbehandling d
SET behandling_id = (select k.id from klage.klagebehandling k where d.id = k.vedtak_id)
where behandling_id is null;

--Set FK constraint
ALTER TABLE klage.delbehandling
    ADD CONSTRAINT fk_behandling_delbehandling
        FOREIGN KEY (behandling_id)
            REFERENCES klage.klagebehandling (id);

--Make new FK not nullable
ALTER TABLE klage.delbehandling
    ALTER COLUMN behandling_id SET NOT NULL;

--Rename to klagebehandling to generic name
ALTER TABLE klage.klagebehandling
    RENAME TO behandling;

--Add discriminator
ALTER TABLE klage.behandling
    ADD COLUMN behandling_type TEXT NOT NULL default 'klage';

--Rename FKs to generic name

ALTER TABLE klage.tildelinghistorikk
    RENAME COLUMN klagebehandling_id TO behandling_id;

ALTER TABLE klage.saksdokument
    RENAME COLUMN klagebehandling_id TO behandling_id;

-- Move medunderskriver to delbehandling

--Add/move column for flyt to delbehandling, default to 'IKKE_SENDT'
ALTER TABLE klage.delbehandling
    ADD COLUMN medunderskriverflyt_id TEXT NOT NULL DEFAULT 1;

--Move data to delbehandling
UPDATE klage.delbehandling d
SET medunderskriverflyt_id = (select b.medunderskriverflyt_id from klage.behandling b where d.id = b.vedtak_id);

--Drop now unused column
ALTER TABLE klage.behandling
    DROP COLUMN medunderskriverflyt_id;

--Add/move column for medundeskriverident to delbehandling
ALTER TABLE klage.delbehandling
    ADD COLUMN medunderskriverident TEXT;

--Move data to delbehandling
UPDATE klage.delbehandling d
SET medunderskriverident = (select b.medunderskriverident from klage.behandling b where d.id = b.vedtak_id);

--Drop now unused column
ALTER TABLE klage.behandling
    DROP COLUMN medunderskriverident;

--Add/move column for dato_sendt_medunderskriver to delbehandling
ALTER TABLE klage.delbehandling
    ADD COLUMN dato_sendt_medunderskriver TIMESTAMP;

--Move data to delbehandling
UPDATE klage.delbehandling d
SET dato_sendt_medunderskriver = (select b.dato_sendt_medunderskriver from klage.behandling b where d.id = b.vedtak_id);

--Drop now unused column
ALTER TABLE klage.behandling
    DROP COLUMN dato_sendt_medunderskriver;

--Remove unused vedtak_id from behandling
ALTER TABLE klage.behandling
    DROP COLUMN vedtak_id;