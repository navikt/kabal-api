create table klage.revision
(
    id        bigint not null,
    actor     text,
    request   text,
    timestamp timestamp,
    primary key (id)
);

create sequence klage.revision_seq start with 1 increment by 1;

create table klage.behandling_aud
(
    id                                         uuid   not null,
    rev                                        bigint not null,
    behandling_type                            text   not null,
    revtype                                    smallint,
    created                                    timestamp,
    dvh_referanse                              text,
    sak_fagsak_id                              text,
    sak_fagsystem                              text,
    feilregistrering_fagsystem_id              text,
    feilregistrering_nav_ident                 text,
    feilregistrering_navn                      text,
    feilregistrering_reason                    text,
    feilregistrering_registered                timestamp,
    dato_behandling_avsluttet                  timestamp,
    dato_behandling_avsluttet_av_saksbehandler timestamp,
    ferdigstilling_nav_ident                   text,
    ferdigstilling_navn                        text,
    frist                                      date,
    gosys_oppgave_id                           bigint,
    oppgave_returned_kommentar                 text,
    oppgave_returned_mappe_id                  bigint,
    oppgave_returned_tildelt_enhetsnummer      text,
    ignore_gosys_oppgave                       boolean,
    kilde_referanse                            text,
    klager_type                                text,
    klager_value                               text,
    medunderskriverident                       text,
    dato_sendt_medunderskriver                 timestamp,
    medunderskriver_flow_state_id              text,
    modified                                   timestamp,
    dato_mottatt_klageinstans                  timestamp,
    previous_saksbehandlerident                text,
    prosessfullmektig_address_adresselinje_1   text,
    prosessfullmektig_address_adresselinje_2   text,
    prosessfullmektig_address_adresselinje_3   text,
    prosessfullmektig_address_landkode         text,
    prosessfullmektig_address_postnummer       text,
    prosessfullmektig_address_poststed         text,
    prosessfullmektig_navn                     text,
    prosessfullmektig_type                     text,
    prosessfullmektig_value                    text,
    rol_flow_state_id                          text,
    rol_ident                                  text,
    rol_returned_date                          timestamp,
    saken_gjelder_type                         text,
    saken_gjelder_value                        text,
    satt_paa_vent_from                         date,
    satt_paa_vent_reason                       text,
    satt_paa_vent_to                           date,
    tilbakekreving                             boolean,
    tildelt_enhet                              text,
    tildelt_saksbehandlerident                 text,
    dato_behandling_tildelt                    timestamp,
    type_id                                    text,
    utfall_id                                  text,
    ytelse_id                                  text,
    anke_behandlende_enhet                     text,
    kaka_kvalitetsvurdering_id                 uuid,
    kaka_kvalitetsvurdering_version            integer,
    kjennelse_mottatt                          timestamp,
    source_behandling_id                       uuid,
    varslet_behandlingstid_unit_type_id        text,
    varslet_behandlingstid_units               integer,
    varslet_frist                              date,
    klage_behandlende_enhet                    text,
    klage_vedtaks_dato                         date,
    mottak_id                                  uuid,
    ny_ankebehandling_ka                       timestamp,
    ny_behandling_etter_tr_opphevet            timestamp,
    sendt_til_trygderetten                     timestamp,
    avsender_enhet_foersteinstans              text,
    kommentar_fra_foersteinstans               text,
    dato_mottatt_foersteinstans                date,
    primary key (rev, id)
);

create table klage.behandling_extra_utfall_aud
(
    rev           bigint not null,
    behandling_id uuid   not null,
    id            text   not null,
    revtype       smallint,
    primary key (behandling_id, rev, id)
);

create table klage.behandling_hjemmel_aud
(
    rev           bigint not null,
    behandling_id uuid   not null,
    id            text   not null,
    revtype       smallint,
    primary key (behandling_id, rev, id)
);

create table klage.behandling_registreringshjemmel_aud
(
    rev           bigint not null,
    behandling_id uuid   not null,
    id            text   not null,
    revtype       smallint,
    primary key (behandling_id, rev, id)
);

create table klage.behandling_saksdokument_aud
(
    rev           bigint not null,
    behandling_id uuid   not null,
    id            uuid   not null,
    revtype       smallint,
    primary key (behandling_id, rev, id)
);

create table klage.saksdokument_aud
(
    id               uuid   not null,
    rev              bigint not null,
    revtype          smallint,
    dokument_info_id text,
    journalpost_id   text,
    primary key (rev, id)
);

alter table klage.behandling_aud
    add constraint fk_behandling_aud foreign key (rev) references klage.revision;
alter table klage.behandling_extra_utfall_aud
    add constraint fk_behandling_extra_utfall_aud foreign key (rev) references klage.revision;
alter table klage.behandling_hjemmel_aud
    add constraint fk_behandling_hjemmel_aud foreign key (rev) references klage.revision;
alter table klage.behandling_registreringshjemmel_aud
    add constraint fk_behandling_registreringshjemmel_aud foreign key (rev) references klage.revision;
alter table klage.saksdokument_aud
    add constraint fk_saksdokument_aud foreign key (rev) references klage.revision;
alter table if exists klage.behandling_saksdokument_aud
    add constraint fk_behandling_saksdokument_aud foreign key (rev) references klage.revision;