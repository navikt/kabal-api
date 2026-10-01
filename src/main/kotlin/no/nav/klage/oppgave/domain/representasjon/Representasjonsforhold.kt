package no.nav.klage.oppgave.domain.representasjon

import no.nav.klage.kodeverk.Tema

data class Representasjonsforhold(
    val fullmakt: List<Fullmaktsforhold>,
    val vergemaal: List<Vergemaalsforhold>,
)

data class Fullmaktsforhold(
    val fullmaktsgiver: String,
    val fullmektig: String,
    val leserettigheter: Set<Tema>,
    val skriverettigheter: Set<Tema>,
)

data class Vergemaalsforhold(
    val vergehaver: String,
    val verge: String,
    val leserettigheter: Set<Tema>,
    val skriverettigheter: Set<Tema>,
)
