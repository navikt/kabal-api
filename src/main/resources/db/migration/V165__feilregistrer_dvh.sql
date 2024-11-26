INSERT INTO klage.kafka_event(id, behandling_id, kilde, kilde_referanse, json_payload, status_id, type, created)
VALUES ('b3506d5a-450e-4d2d-8bdd-6744c562cb00',
        '4413a5e2-beb7-4fbb-a4ee-8a4b7dfc9d70',
        'FS36',
        '54380f59-7695-4a5a-acf1-59d84542f9eb',
        '{"eventId":"b3506d5a-450e-4d2d-8bdd-6744c562cb00","ansvarligEnhetKode":"4292","ansvarligEnhetType":"NORG","avsender":"Kabal","behandlingId":"54380f59-7695-4a5a-acf1-59d84542f9eb","behandlingIdKabal":"4413a5e2-beb7-4fbb-a4ee-8a4b7dfc9d70","behandlingStartetKA":"2023-05-10","behandlingStatus":"AVSLUTTET","behandlingType":"ANKE","beslutter":"A100182","endringstid":"2024-02-06T10:38:19.045987","hjemmel":[],"klager":null,"opprinneligFagsaksystem":"FS36","opprinneligFagsakId":"152089639","overfoertKA":"2022-09-02","resultat":"Feilregistrert","sakenGjelder":null,"saksbehandler":"H154142","saksbehandlerEnhet":"4292","tekniskTid":"2024-11-26T16:42:34.129146","vedtaksdato":"2024-02-06","versjon":1,"ytelseType":"FOR_ENG"}',
        'IKKE_SENDT',
        'STATS_DVH',
        now());

INSERT INTO klage.kafka_event(id, behandling_id, kilde, kilde_referanse, json_payload, status_id, type, created)
VALUES ('b686f7e9-e3f0-4de1-9714-f1e01119b72c',
        '9ac123d3-6ced-4f26-9fe7-0bc70346dbd0',
        'FS36',
        'cf4d9167-3de1-4751-b147-569f4058f044',
        '{"eventId":"b686f7e9-e3f0-4de1-9714-f1e01119b72c","ansvarligEnhetKode":null,"ansvarligEnhetType":"NORG","avsender":"Kabal","behandlingId":"cf4d9167-3de1-4751-b147-569f4058f044","behandlingIdKabal":"9ac123d3-6ced-4f26-9fe7-0bc70346dbd0","behandlingStartetKA":"2024-06-13","behandlingStatus":"AVSLUTTET","behandlingType":"ANKE","beslutter":null,"endringstid":"2022-06-10T00:00:00.000000","hjemmel":["Folketrygdloven-§ 14-11"],"klager":null,"opprinneligFagsaksystem":"FS36","opprinneligFagsakId":"152049991","overfoertKA":"2022-06-10","resultat":"Feilregistrert","sakenGjelder":null,"saksbehandler":"S157894","saksbehandlerEnhet":"4292","tekniskTid":"2024-11-26T11:21:08.454886","vedtaksdato":null,"versjon":1,"ytelseType":"FOR_FOR"}',
        'IKKE_SENDT',
        'STATS_DVH',
        now());