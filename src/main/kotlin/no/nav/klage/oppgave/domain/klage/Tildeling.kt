package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class Tildeling(
    val saksbehandlerident: String?,
    val enhet: String?,
    val tidspunkt: LocalDateTime,
)