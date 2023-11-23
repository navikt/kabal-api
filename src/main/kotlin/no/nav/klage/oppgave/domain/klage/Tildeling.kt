package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class Tildeling(
    val saksbehandlerident: String?,
    val enhet: String?,
    val tidspunkt: LocalDateTime,
)

@Embeddable
data class TildelingWithReason(
    val saksbehandlerident: String?,
    val enhet: String?,
    val tidspunkt: LocalDateTime,
    val reason: String?,
    @Column(name = "utfoerende_ident")
    val utfoerendeIdent: String,
)
