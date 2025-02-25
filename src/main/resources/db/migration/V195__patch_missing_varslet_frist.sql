--ytelse_id = 1
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id = '92189a6e-afe4-482d-b137-661f860a2f20';
--ytelse_id = 17
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id IN (
             '11191445-cb0b-40ec-9421-5039e96c5e1e',
             '64d4eb76-b047-4526-a8ab-579ca8ab932f',
             '3c457500-7385-47fb-94f4-17bc075a2731',
             '7e9b6b77-45ea-4088-b457-9a0a29d30e27',
             '6ec424b2-da9e-4dfa-a060-e6f213317fdb',
             '29334dd2-dbd9-44a2-813c-a56f5a900bdb',
             '3955fd7f-c67e-4737-abf7-363f662beba4',
             '68626949-fd2d-43b5-ab0f-774975bdf74a');
--ytelse_id = 25
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '4 months')::date,
    varslet_behandlingstid_unit_type_id = '2',
    varslet_behandlingstid_units        = 4
WHERE id IN (
             '8c7e3fac-287f-4346-b24d-824e1b5c991a',
             '8e3db198-2d09-448b-af95-dc949160b78a',
             '7748f7b9-1f4f-4bf8-a8da-7c1daa7ccd6f'
    );
--ytelse_id = 30
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id IN (
             '41f39590-256f-4daf-8ed2-11708bc47d65',
             '403156ef-99a8-4148-8f72-caa059d712e3'
    );
--ytelse_id = 32
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id = 'dd574c52-ddcb-4d82-8d92-7d18c59eb801';
--ytelse_id = 35
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '4 months')::date,
    varslet_behandlingstid_unit_type_id = '2',
    varslet_behandlingstid_units        = 4
WHERE id IN (
             'd6543a06-d2c3-4812-b8cb-cfe16d813937',
             'b88ec117-b2de-4310-8c01-cc79d93616f6',
             'a02f2f96-9d13-4f08-bb5b-f6a30a73d4ad',
             '00e48c64-bc2e-44fd-b1e8-589957f1da1a',
             '3669bca7-f71d-47cb-8d34-bb27cdd3ce63',
             'c970b876-22f5-4ada-b3b0-a6f30e839319',
             '45fd6f99-7e7d-4b7c-bc97-80aaf62dcc37',
             '2efe28b8-4282-47f7-b9c2-4a7fcf9d7fa7',
             'f9fb0294-a30a-4d70-a078-21825c659175',
             '85d6c5e3-da27-4141-ba90-6de19ab52a71',
             '47bd61b0-2a91-4c54-b959-fc49646d521a',
             '4c57c702-5802-4df0-bcae-6e11bc257195',
             'b913820f-43c3-4970-9daf-4cfbcc923c0d',
             'b3da3768-00f8-4449-ad3b-1cc5054ebceb',
             '49c32eb7-8041-46df-9f76-1034329081f5',
             'a79a3194-66fe-4c96-ab66-aa5f48cb7379',
             '663fb07c-930e-44fc-8099-e06be0080171'
    );
--ytelse_id = 6
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id IN (
             '6febecf0-cea4-4efe-90b4-6231e3255e3c',
             '30fe82c0-5ff3-4608-a417-175a5128e648',
             'c93ddec7-5f9e-4c8d-9b97-4deeac53ecdf'
    );

--ytelse_id = 7
UPDATE klage.behandling
SET varslet_frist                       = (dato_mottatt_klageinstans + interval '12 weeks')::date,
    varslet_behandlingstid_unit_type_id = '1',
    varslet_behandlingstid_units        = 12
WHERE id = '7ac59784-c955-45c1-abd4-7c2da8bdee12';
--merkantil_task_list

UPDATE klage.task_list_merkantil
SET date_handled = now(),
    handled_by = 'W132204',
    handled_by_name = 'Øyvind Norsted Wedøe',
    comment = 'Varslet merkantil, oppdatert varslet frist i db'
WHERE behandling_id IN (
                        'b88ec117-b2de-4310-8c01-cc79d93616f6',
                        '663fb07c-930e-44fc-8099-e06be0080171',
                        '68626949-fd2d-43b5-ab0f-774975bdf74a',
                        '403156ef-99a8-4148-8f72-caa059d712e3',
                        'c93ddec7-5f9e-4c8d-9b97-4deeac53ecdf',
                        '3955fd7f-c67e-4737-abf7-363f662beba4',
                        'd6543a06-d2c3-4812-b8cb-cfe16d813937',
                        'a79a3194-66fe-4c96-ab66-aa5f48cb7379',
                        '49c32eb7-8041-46df-9f76-1034329081f5',
                        '7e9b6b77-45ea-4088-b457-9a0a29d30e27',
                        '6ec424b2-da9e-4dfa-a060-e6f213317fdb',
                        '3c457500-7385-47fb-94f4-17bc075a2731',
                        '6febecf0-cea4-4efe-90b4-6231e3255e3c',
                        '64d4eb76-b047-4526-a8ab-579ca8ab932f',
                        '30fe82c0-5ff3-4608-a417-175a5128e648',
                        'b913820f-43c3-4970-9daf-4cfbcc923c0d',
                        'b3da3768-00f8-4449-ad3b-1cc5054ebceb',
                        '4c57c702-5802-4df0-bcae-6e11bc257195',
                        '8e3db198-2d09-448b-af95-dc949160b78a',
                        '47bd61b0-2a91-4c54-b959-fc49646d521a',
                        '11191445-cb0b-40ec-9421-5039e96c5e1e',
                        '41f39590-256f-4daf-8ed2-11708bc47d65',
                        '85d6c5e3-da27-4141-ba90-6de19ab52a71',
                        '92189a6e-afe4-482d-b137-661f860a2f20',
                        'dd574c52-ddcb-4d82-8d92-7d18c59eb801',
                        '8c7e3fac-287f-4346-b24d-824e1b5c991a',
                        'f9fb0294-a30a-4d70-a078-21825c659175',
                        '2efe28b8-4282-47f7-b9c2-4a7fcf9d7fa7',
                        '45fd6f99-7e7d-4b7c-bc97-80aaf62dcc37',
                        'c970b876-22f5-4ada-b3b0-a6f30e839319',
                        '3669bca7-f71d-47cb-8d34-bb27cdd3ce63',
                        '00e48c64-bc2e-44fd-b1e8-589957f1da1a',
                        '7ac59784-c955-45c1-abd4-7c2da8bdee12',
                        '7748f7b9-1f4f-4bf8-a8da-7c1daa7ccd6f',
                        'a02f2f96-9d13-4f08-bb5b-f6a30a73d4ad',
                        '29334dd2-dbd9-44a2-813c-a56f5a900bdb'
    );
