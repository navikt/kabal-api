create table klage.dokument_under_arbeid_aud
(
    id                                     uuid   not null,
    rev                                    bigint not null,
    dokument_under_arbeid_type             text   not null,
    revtype                                smallint,
    behandling_id                          uuid,
    created                                timestamp,
    creator_ident                          text,
    creator_role                           text check (creator_role in ('KABAL_SAKSBEHANDLING', 'KABAL_ROL',
                                                                        'KABAL_MEDUNDERSKRIVER', 'NONE')),
    ferdigstilt                            timestamp,
    markert_ferdig                         timestamp,
    markert_ferdig_by                      text,
    modified                               timestamp,
    name                                   text,
    parent_id                              uuid,
    language                               text check (language in ('NN', 'NB')),
    mellomlager_id                         text,
    mellomlagret_date                      timestamp,
    mellomlagret_version                   integer,
    size                                   bigint,
    smarteditor_id                         uuid,
    smarteditor_template_id                text,
    journalfoert_dokument_dokument_info_id text,
    journalfoert_dokument_journalpost_id   text,
    opprettet                              timestamp,
    sort_key                               text,
    dokument_enhet_id                      uuid,
    dokument_type_id                       text,
    journalfoerende_enhet_id               text,
    dato_mottatt                           date,
    inngaaende_kanal                       text check (inngaaende_kanal in ('ALTINN_INNBOKS', 'E_POST')),
    primary key (rev, id)
);

create table klage.dua_dokument_under_arbeid_avsender_mottaker_info_aud
(
    rev                      bigint not null,
    dokument_under_arbeid_id uuid   not null,
    id                       uuid   not null,
    revtype                  smallint,
    primary key (dokument_under_arbeid_id, rev, id)
);

create table klage.dokument_under_arbeid_avsender_mottaker_info_aud
(
    id                     uuid   not null,
    rev                    bigint not null,
    revtype                smallint,
    address_adresselinje_1 text,
    address_adresselinje_2 text,
    address_adresselinje_3 text,
    address_landkode       text,
    address_postnummer     text,
    address_poststed       text,
    force_central_print    boolean,
    identifikator          text,
    local_print            boolean,
    navn                   text,
    primary key (rev, id)
);

create table klage.dua_dokument_under_arbeid_dokarkiv_reference_aud
(
    rev                      bigint not null,
    dokument_under_arbeid_id uuid   not null,
    id                       uuid   not null,
    revtype                  smallint,
    primary key (dokument_under_arbeid_id, rev, id)
);

create table klage.dokument_under_arbeid_dokarkiv_reference_aud
(
    id               uuid   not null,
    rev              bigint not null,
    revtype          smallint,
    dokument_info_id text,
    journalpost_id   text,
    primary key (rev, id)
);

alter table if exists klage.dokument_under_arbeid_aud
    add constraint fk_dokument_under_arbeid_aud
        foreign key (rev)
            references klage.revision;

alter table if exists klage.dua_dokument_under_arbeid_avsender_mottaker_info_aud
    add constraint fk_dua_dua_avs_mot_aud
        foreign key (rev)
            references klage.revision;

alter table if exists klage.dokument_under_arbeid_avsender_mottaker_info_aud
    add constraint fk_dua_avs_mot_aud
        foreign key (rev)
            references klage.revision;

alter table if exists klage.dua_dokument_under_arbeid_dokarkiv_reference_aud
    add constraint fk_dua_dua_dokarkiv_reference_aud
        foreign key (rev)
            references klage.revision;

alter table if exists klage.dokument_under_arbeid_dokarkiv_reference_aud
    add constraint fk_dua_dokarkiv_reference_aud
        foreign key (rev)
            references klage.revision;