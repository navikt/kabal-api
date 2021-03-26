CREATE TABLE klage.mottak
(
    id                               UUID PRIMARY KEY,
    versjon                          BIGINT                   NOT NULL,
    tema_id                          VARCHAR(3)               NOT NULL,
    sakstype_id                      VARCHAR(10)              NOT NULL,
    referanse_id                     TEXT,
    innsyn_url                       TEXT,
    foedselsnummer                   VARCHAR(11),
    organisasjonsnummer              VARCHAR(9),
    virksomhetsnummer                VARCHAR(9),
    hjemmel_liste                    TEXT,
    beskrivelse                      TEXT,
    avsender_saksbehandlerident      VARCHAR(50),
    avsender_enhet                   VARCHAR(10),
    oversendt_klageinstans_enhet     VARCHAR(10),
    oversendelsesbrev_journalpost_id VARCHAR(40),
    brukers_klage_journalpost_id     VARCHAR(40),
    dato_innsendt                    DATE,
    dato_mottatt_foersteinstans      DATE,
    dato_oversendt_klageinstans      DATE                     NOT NULL,
    dato_frist_fra_foersteinstans    DATE,
    kilde                            VARCHAR(50)              NOT NULL,
    created                          TIMESTAMP WITH TIME ZONE NOT NULL,
    modified                         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_mottak_sakstype
        FOREIGN KEY (sakstype_id)
            REFERENCES kodeverk.sakstype (id),
    CONSTRAINT fk_mottak_tema
        FOREIGN KEY (tema_id)
            REFERENCES kodeverk.tema (id)
);

CREATE TABLE klage.kvalitetsvurdering
(
    id                          UUID PRIMARY KEY,
    grunn_id                    INTEGER,
    eoes_id                     INTEGER,
    raadfoert_med_lege_id       INTEGER,
    intern_vurdering            TEXT,
    send_tilbakemelding         BOOLEAN,
    tilbakemelding              TEXT,
    mottaker_saksbehandlerident VARCHAR(50),
    mottaker_enhet              VARCHAR(10),
    created                     TIMESTAMP WITH TIME ZONE NOT NULL,
    modified                    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_kvalitetsvurdering_grunn
        FOREIGN KEY (grunn_id)
            REFERENCES kodeverk.grunn (id),
    CONSTRAINT fk_kvalitetsvurdering_eoes
        FOREIGN KEY (eoes_id)
            REFERENCES kodeverk.eoes (id),
    CONSTRAINT fk_kvalitetsvurdering_rol
        FOREIGN KEY (raadfoert_med_lege_id)
            REFERENCES kodeverk.raadfoert_med_lege (id)
);

CREATE TABLE klage.klagebehandling
(
    id                                         UUID PRIMARY KEY,
    versjon                                    BIGINT                   NOT NULL,
    foedselsnummer                             VARCHAR(11),
    tema_id                                    VARCHAR(3)               NOT NULL,
    sakstype_id                                VARCHAR(10)              NOT NULL,
    referanse_id                               TEXT,
    dato_innsendt                              DATE,
    dato_mottatt_foersteinstans                DATE,
    dato_mottatt_klageinstans                  DATE                     NOT NULL,
    dato_behandling_startet                    DATE,
    dato_behandling_avsluttet                  DATE,
    frist                                      DATE,
    tildelt_saksbehandlerident                 VARCHAR(50),
    tildelt_enhet                              VARCHAR(10),
    avsender_enhet_foersteinstans              VARCHAR(10),
    avsender_saksbehandlerident_foersteinstans VARCHAR(50),
    mottak_id                                  UUID                     NOT NULL,
    kvalitetsvurdering_id                      UUID,
    kilde                                      VARCHAR(15)              NOT NULL,
    created                                    TIMESTAMP WITH TIME ZONE NOT NULL,
    modified                                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_klagebehandling_sakstype
        FOREIGN KEY (sakstype_id)
            REFERENCES kodeverk.sakstype (id),
    CONSTRAINT fk_behandling_kvalitetsvurdering
        FOREIGN KEY (kvalitetsvurdering_id)
            REFERENCES klage.kvalitetsvurdering (id),
    CONSTRAINT fk_behandling_mottak
        FOREIGN KEY (mottak_id)
            REFERENCES klage.mottak (id)
);

CREATE TABLE klage.vedtak
(
    id                 UUID PRIMARY KEY,
    utfall_id          INTEGER                  NOT NULL,
    klagebehandling_id UUID                     NOT NULL,
    modified           TIMESTAMP WITH TIME ZONE NOT NULL,
    created            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_vedtak_utfall
        FOREIGN KEY (utfall_id)
            REFERENCES kodeverk.utfall (id),
    CONSTRAINT fk_vedtak_klagebehandling
        FOREIGN KEY (klagebehandling_id)
            REFERENCES klage.klagebehandling (id)
);

CREATE TABLE klage.hjemmel
(
    id       UUID PRIMARY KEY,
    lov_id   INTEGER,
    kapittel INTEGER,
    paragraf INTEGER,
    ledd     INTEGER,
    bokstav  VARCHAR(1),
    original TEXT NOT NULL,
    CONSTRAINT fk_hjemmel_lov
        FOREIGN KEY (lov_id)
            REFERENCES kodeverk.lov (id)
);

CREATE TABLE klage.saksdokument
(
    id                 UUID PRIMARY KEY,
    klagebehandling_id UUID NOT NULL,
    journalpost_id     TEXT,
    dokument_info_id   TEXT,
    CONSTRAINT fk_saksdokument_klagebehandling
        FOREIGN KEY (klagebehandling_id)
            REFERENCES klage.klagebehandling (id)
);

CREATE TABLE klage.klagebehandling_hjemmel
(
    klagebehandling_id UUID NOT NULL,
    hjemmel_id         UUID NOT NULL,
    CONSTRAINT fk_hjemmel_klagebehandling
        FOREIGN KEY (klagebehandling_id)
            REFERENCES klage.klagebehandling (id),
    CONSTRAINT fk_klagebehandling_hjemmel
        FOREIGN KEY (hjemmel_id)
            REFERENCES klage.hjemmel (id)
);

CREATE TABLE klage.vedtak_hjemmel
(
    vedtak_id  UUID NOT NULL,
    hjemmel_id UUID NOT NULL,
    CONSTRAINT fk_hjemmel_vedtak
        FOREIGN KEY (vedtak_id)
            REFERENCES klage.vedtak (id),
    CONSTRAINT fk_vedtak_hjemmel
        FOREIGN KEY (hjemmel_id)
            REFERENCES klage.hjemmel (id)
);

CREATE TABLE klage.endringslogginnslag
(
    id                 UUID PRIMARY KEY,
    klagebehandling_id UUID                     NOT NULL,
    saksbehandlerident VARCHAR(50),
    kilde              VARCHAR(20)              NOT NULL,
    handling           VARCHAR(20)              NOT NULL,
    felt               VARCHAR(50)              NOT NULL,
    fraverdi           TEXT,
    tilverdi           TEXT,
    tidspunkt          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_endringslogginnslag_klagebehandling
        FOREIGN KEY (klagebehandling_id)
            REFERENCES klage.klagebehandling (id)
);
