ALTER TABLE klage.fullmektighistorikk
    ADD COLUMN fullmektig_name TEXT;

ALTER TABLE klage.varslet_behandlingstid_historikk_mottaker_info
    ALTER COLUMN varslet_behandlingstid_historikk_mottaker_type DROP NOT NULL;

ALTER TABLE klage.varslet_behandlingstid_historikk_mottaker_info
    ALTER COLUMN varslet_behandlingstid_historikk_mottaker_value DROP NOT NULL;