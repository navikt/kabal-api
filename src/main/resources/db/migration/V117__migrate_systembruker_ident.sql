UPDATE klage.satt_paa_vent_historikk
SET utfoerende_ident  = 'SYSTEMBRUKER'
WHERE utfoerende_ident = 'SYSTEM';

UPDATE klage.behandling
SET feilregistrering_nav_ident  = 'SYSTEMBRUKER'
WHERE feilregistrering_nav_ident = 'SYSTEM';
