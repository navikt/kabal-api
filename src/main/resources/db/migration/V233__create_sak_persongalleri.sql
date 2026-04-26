CREATE TABLE klage.sak_persongalleri
(
    id             UUID PRIMARY KEY,
    sak_fagsystem  TEXT NOT NULL,
    sak_fagsak_id  TEXT NOT NULL,
    foedselsnummer TEXT NOT NULL,
    CONSTRAINT uc_sak_persongalleri UNIQUE (sak_fagsystem, sak_fagsak_id, foedselsnummer)
);

CREATE INDEX idx_sak_persongalleri_fagsystem_fagsakid ON klage.sak_persongalleri (sak_fagsystem, sak_fagsak_id);

CREATE TABLE klage.person_protection
(
    id                UUID PRIMARY KEY,
    foedselsnummer    TEXT    NOT NULL UNIQUE,
    fortrolig         BOOLEAN NOT NULL,
    strengt_fortrolig BOOLEAN NOT NULL,
    skjermet          BOOLEAN NOT NULL
);