UPDATE klage.kafka_event
SET status_id = 'IKKE_SENDT'
WHERE status_id = '1';

ALTER TABLE klage.kafka_event
    ALTER COLUMN status_id SET DEFAULT 'IKKE_SENDT';



INSERT INTO klage.kafka_event(id, behandling_id, kilde, kilde_referanse, json_payload, status_id, type, created)
VALUES ('b3506d5a-450e-4d2d-8bdd-6744c562cb00',
        'c64046ba-e0d8-485e-9d62-acfabecf3e1b',
        'FS36',
        'cf4d9167-3de1-4751-b147-569f4058f044',
        '{"eventId":"b3506d5a-450e-4d2d-8bdd-6744c562cb00","ansvarligEnhetKode":"4292","ansvarligEnhetType":"NORG","avsender":"Kabal","behandlingId":"cf4d9167-3de1-4751-b147-569f4058f044","behandlingIdKabal":"c64046ba-e0d8-485e-9d62-acfabecf3e1b","behandlingStartetKA":"2024-02-01","behandlingStatus":"AVSLUTTET","behandlingType":"ANKE","beslutter":null,"endringstid":"2024-02-02T09:48:20.235016","hjemmel":["Folketrygdloven-§ 14-11","Folketrygdloven-§ 14-10"],"klager":null,"opprinneligFagsaksystem":"FS36","opprinneligFagsakId":"152049991","overfoertKA":"2022-06-15","resultat":"Feilregistrert","sakenGjelder":null,"saksbehandler":"H154142","saksbehandlerEnhet":"4292","tekniskTid":"2024-11-26T19:30:34.084380","vedtaksdato":"2024-02-02","versjon":1,"ytelseType":"FOR_FOR"}',
        'IKKE_SENDT',
        'STATS_DVH',
        now());

INSERT INTO klage.kafka_event(id, behandling_id, kilde, kilde_referanse, json_payload, status_id, type, created)
VALUES ('56728a0e-1251-45ec-9073-121679daa861',
        '9ac123d3-6ced-4f26-9fe7-0bc70346dbd0',
        'FS36',
        'cf4d9167-3de1-4751-b147-569f4058f044',
        '{"eventId":"56728a0e-1251-45ec-9073-121679daa861","ansvarligEnhetKode":null,"ansvarligEnhetType":"NORG","avsender":"Kabal","behandlingId":"cf4d9167-3de1-4751-b147-569f4058f044","behandlingIdKabal":"9ac123d3-6ced-4f26-9fe7-0bc70346dbd0","behandlingStartetKA":"2024-06-13","behandlingStatus":"AVSLUTTET","behandlingType":"ANKE","beslutter":null,"endringstid":"2024-06-13T12:19:44.200466","hjemmel":["Folketrygdloven-§ 14-11"],"klager":null,"opprinneligFagsaksystem":"FS36","opprinneligFagsakId":"152049991","overfoertKA":"2024-01-15","resultat":"Feilregistrert","sakenGjelder":null,"saksbehandler":"S157894","saksbehandlerEnhet":"4292","tekniskTid":"2024-11-26T19:30:08.166234","vedtaksdato":null,"versjon":1,"ytelseType":"FOR_FOR"}',
        'IKKE_SENDT',
        'STATS_DVH',
        now());

INSERT INTO klage.kafka_event(id, behandling_id, kilde, kilde_referanse, json_payload, status_id, type, created)
VALUES ('ef59687e-1749-4d69-94bf-2c3bc9bd6ee4',
        'e6c4bad5-a53d-4f9f-bfa2-3a31225ded3a',
        'FS36',
        '54380f59-7695-4a5a-acf1-59d84542f9eb',
        '{"eventId":"ef59687e-1749-4d69-94bf-2c3bc9bd6ee4","ansvarligEnhetKode":null,"ansvarligEnhetType":"NORG","avsender":"Kabal","behandlingId":"54380f59-7695-4a5a-acf1-59d84542f9eb","behandlingIdKabal":"e6c4bad5-a53d-4f9f-bfa2-3a31225ded3a","behandlingStartetKA":"2024-02-06","behandlingStatus":"AVSLUTTET","behandlingType":"ANKE","beslutter":null,"endringstid":"2024-02-06T10:41:32.140155","hjemmel":[],"klager":null,"opprinneligFagsaksystem":"FS36","opprinneligFagsakId":"152089639","overfoertKA":"2022-09-02","resultat":"Feilregistrert","sakenGjelder":null,"saksbehandler":"H154142","saksbehandlerEnhet":"4292","tekniskTid":"2024-11-26T19:30:48.098498","vedtaksdato":null,"versjon":1,"ytelseType":"FOR_ENG"}',
        'IKKE_SENDT',
        'STATS_DVH',
        now());