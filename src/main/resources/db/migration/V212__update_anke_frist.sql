UPDATE klage.behandling
SET frist = dato_mottatt_klageinstans
WHERE type_id = '2'
  AND dato_behandling_avsluttet_av_saksbehandler IS NULL
  AND feilregistrering_registered IS NULL;