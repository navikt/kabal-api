DELETE
FROM klage.behandling_registreringshjemmel
WHERE behandling_id IN (
                        'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
                        '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
                        'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
                        '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
                        '86fa4f57-96ca-496d-9b77-60685811a435',
                        'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
                        'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
                        '80b999e6-e88a-46dd-803c-341ca4703e57',
                        '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
                        'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
                        '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
                        '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
                        'c141dab6-60c8-4a82-a175-8c85146fca00',
                        'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
                        '442fad43-3c3e-4574-a197-76c5f38d4a7f',
                        '4489841e-a86c-4104-90a3-f88875ce1d73',
                        'bc21698a-b081-4265-af15-ff3539a66748',
                        'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
                        '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
                        'c24056a7-d3b4-4bac-b101-b260e85f9023',
                        'f73291a9-730b-4bda-8e25-ecf1f236b807',
                        '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );

DELETE
FROM klage.behandling_hjemmel
WHERE behandling_id IN (
                        'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
                        '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
                        'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
                        '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
                        '86fa4f57-96ca-496d-9b77-60685811a435',
                        'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
                        'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
                        '80b999e6-e88a-46dd-803c-341ca4703e57',
                        '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
                        'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
                        '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
                        '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
                        'c141dab6-60c8-4a82-a175-8c85146fca00',
                        'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
                        '442fad43-3c3e-4574-a197-76c5f38d4a7f',
                        '4489841e-a86c-4104-90a3-f88875ce1d73',
                        'bc21698a-b081-4265-af15-ff3539a66748',
                        'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
                        '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
                        'c24056a7-d3b4-4bac-b101-b260e85f9023',
                        'f73291a9-730b-4bda-8e25-ecf1f236b807',
                        '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );

DELETE
FROM klage.endringslogginnslag
WHERE behandling_id IN (
                        'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
                        '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
                        'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
                        '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
                        '86fa4f57-96ca-496d-9b77-60685811a435',
                        'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
                        'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
                        '80b999e6-e88a-46dd-803c-341ca4703e57',
                        '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
                        'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
                        '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
                        '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
                        'c141dab6-60c8-4a82-a175-8c85146fca00',
                        'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
                        '442fad43-3c3e-4574-a197-76c5f38d4a7f',
                        '4489841e-a86c-4104-90a3-f88875ce1d73',
                        'bc21698a-b081-4265-af15-ff3539a66748',
                        'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
                        '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
                        'c24056a7-d3b4-4bac-b101-b260e85f9023',
                        'f73291a9-730b-4bda-8e25-ecf1f236b807',
                        '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );

DELETE
FROM klage.saksdokument
WHERE behandling_id IN (
                        'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
                        '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
                        'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
                        '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
                        '86fa4f57-96ca-496d-9b77-60685811a435',
                        'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
                        'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
                        '80b999e6-e88a-46dd-803c-341ca4703e57',
                        '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
                        'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
                        '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
                        '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
                        'c141dab6-60c8-4a82-a175-8c85146fca00',
                        'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
                        '442fad43-3c3e-4574-a197-76c5f38d4a7f',
                        '4489841e-a86c-4104-90a3-f88875ce1d73',
                        'bc21698a-b081-4265-af15-ff3539a66748',
                        'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
                        '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
                        'c24056a7-d3b4-4bac-b101-b260e85f9023',
                        'f73291a9-730b-4bda-8e25-ecf1f236b807',
                        '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );

DELETE
FROM klage.kafka_event
WHERE behandling_id IN (
                        'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
                        '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
                        'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
                        '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
                        '86fa4f57-96ca-496d-9b77-60685811a435',
                        'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
                        'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
                        '80b999e6-e88a-46dd-803c-341ca4703e57',
                        '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
                        'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
                        '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
                        '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
                        'c141dab6-60c8-4a82-a175-8c85146fca00',
                        'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
                        '442fad43-3c3e-4574-a197-76c5f38d4a7f',
                        '4489841e-a86c-4104-90a3-f88875ce1d73',
                        'bc21698a-b081-4265-af15-ff3539a66748',
                        'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
                        '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
                        'c24056a7-d3b4-4bac-b101-b260e85f9023',
                        'f73291a9-730b-4bda-8e25-ecf1f236b807',
                        '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );

DELETE
FROM klage.behandling
WHERE id IN (
             'd5e896a8-ca9d-40ea-809a-5037ec9e888c',
             '2e387cb0-9854-460e-86d2-fe8f4cdc7017',
             'e295b1fc-e369-4f41-a2ec-b2ddbddd0cf0',
             '02bb1a1b-98d0-41f7-9b7c-a3ba2129be08',
             '86fa4f57-96ca-496d-9b77-60685811a435',
             'a816e18c-4c90-4eb9-94d8-96e8c49510c5',
             'fef555a0-cc7c-47e1-ab54-2e0d2f9d4969',
             '80b999e6-e88a-46dd-803c-341ca4703e57',
             '104dec7c-5abf-4c91-9826-cf8e6d32f4d7',
             'e5d6f09d-0103-41bb-85ff-15fe96a0575d',
             '6dd6c81b-9723-40db-a4d6-e569b10ee0c7',
             '9fd1ee0f-871a-4a84-ad07-0e786e82a292',
             'c141dab6-60c8-4a82-a175-8c85146fca00',
             'a07d8e4e-a1d5-46ed-8d38-eb8cd63d6034',
             '442fad43-3c3e-4574-a197-76c5f38d4a7f',
             '4489841e-a86c-4104-90a3-f88875ce1d73',
             'bc21698a-b081-4265-af15-ff3539a66748',
             'f2a2b63f-6453-43d6-b54e-e2818c65ee3f',
             '5334d402-60f3-45c1-9f72-bff54d9bc3dc',
             'c24056a7-d3b4-4bac-b101-b260e85f9023',
             'f73291a9-730b-4bda-8e25-ecf1f236b807',
             '6dead83b-ba4b-45a2-8a38-eb8d1b24774e'
    );
