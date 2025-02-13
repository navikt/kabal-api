create table klage.revision
(
    id        integer not null,
    timestamp bigint  not null,
    actor     text,
    request   text,
    primary key (id)
);

create table klage.behandling_aud
(
    id                                         uuid      not null,
    klager_type                                text,
    klager_value                               text,
    prosessfullmektig_type                     text,
    prosessfullmektig_value                    text,
    saken_gjelder_type                         text,
    saken_gjelder_value                        text,
    tema_id                                    text,
    type_id                                    text      not null,
    kilde_referanse                            text      not null,
    sak_fagsystem                              text      not null,
    sak_fagsak_id                              text,
    dato_mottatt_foersteinstans                date,
    dato_mottatt_klageinstans                  timestamp not null,
    dato_behandling_tildelt                    timestamp,
    frist                                      date,
    tildelt_saksbehandlerident                 text,
    tildelt_enhet                              text,
    avsender_enhet_foersteinstans              text,
    previous_saksbehandlerident                text,
    mottak_id                                  uuid,
    kommentar_fra_foersteinstans               text,
    created                                    timestamp not null,
    modified                                   timestamp not null,
    medunderskriver_enhet                      text,
    kaka_kvalitetsvurdering_id                 uuid,
    ytelse_id                                  text,
    dvh_referanse                              text,
    behandling_type                            text      not null,
    klage_vedtaks_dato                         date,
    klage_behandlende_enhet                    text,
    source_behandling_id                       uuid,
    satt_paa_vent_from                         date,
    sendt_til_trygderetten                     timestamp,
    kjennelse_mottatt                          timestamp,
    kaka_kvalitetsvurdering_version            integer   not null,
    feilregistrering_nav_ident                 text,
    feilregistrering_registered                timestamp,
    feilregistrering_reason                    text,
    feilregistrering_fagsystem_id              text,
    satt_paa_vent_to                           date,
    satt_paa_vent_reason                       text,
    utfall_id                                  text,
    dato_behandling_avsluttet_av_saksbehandler timestamp,
    dato_behandling_avsluttet                  timestamp,
    medunderskriverident                       text,
    dato_sendt_medunderskriver                 timestamp,
    medunderskriver_flow_state_id              text,
    rol_ident                                  text,
    rol_flow_state_id                          text      not null,
    ny_ankebehandling_ka                       timestamp,
    rol_returned_date                          timestamp,
    gosys_oppgave_id                           bigint,
    varslet_frist                              date,
    varslet_behandlingstid_units               integer,
    varslet_behandlingstid_unit_type_id        text,
    feilregistrering_navn                      text,
    ferdigstilling_nav_ident                   text,
    ferdigstilling_navn                        text,
    oppgave_returned_tildelt_enhetsnummer      text,
    oppgave_returned_mappe_id                  bigint,
    oppgave_returned_kommentar                 text,
    ny_behandling_etter_tr_opphevet            timestamp,
    anke_behandlende_enhet                     text,
    tilbakekreving                             boolean,
    ignore_gosys_oppgave                       boolean,
    prosessfullmektig_navn                     text,
    prosessfullmektig_address_adressetype      text,
    prosessfullmektig_address_adresselinje_1   text,
    prosessfullmektig_address_adresselinje_2   text,
    prosessfullmektig_address_adresselinje_3   text,
    prosessfullmektig_address_postnummer       text,
    prosessfullmektig_address_poststed         text,
    prosessfullmektig_address_landkode         text,
    rev                                        integer   not null,
    revtype                                    integer,
    primary key (rev, id)
);

create table klage.behandling_extra_utfall_aud
(
    id            text    not null,
    behandling_id uuid    not null,
    rev           integer not null,
    revtype       integer,
    primary key (rev, id, behandling_id)
);

create table klage.behandling_hjemmel_aud
(
    id            text    not null,
    behandling_id uuid    not null,
    rev           integer not null,
    revtype       integer,
    primary key (rev, id, behandling_id)
);

create table klage.behandling_registreringshjemmel_aud
(
    id            text    not null,
    behandling_id uuid    not null,
    rev           integer not null,
    revtype       integer,
    primary key (rev, id, behandling_id)
);

create table klage.saksdokument_aud
(
    id               uuid not null,
    behandling_id    uuid not null,
    journalpost_id   text,
    dokument_info_id text,
    rev           integer not null,
    revtype       integer,
    primary key (rev, id, behandling_id)
);

alter table klage.behandling_aud add constraint fk_behandling_aud foreign key (rev) references revision;
alter table klage.behandling_extra_utfall_aud add constraint fk_behandling_extra_utfall_aud foreign key (rev) references revision;
alter table klage.behandling_hjemmel_aud add constraint fk_behandling_hjemmel_aud foreign key (rev) references revision;
alter table klage.behandling_registreringshjemmel_aud add constraint fk_behandling_registreringshjemmel_aud foreign key (rev) references revision;
alter table klage.saksdokument_aud add constraint fk_saksdokument_aud foreign key (rev) references revision;
