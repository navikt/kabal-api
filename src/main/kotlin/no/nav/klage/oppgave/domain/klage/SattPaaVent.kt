package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Embeddable
import java.time.LocalDate

@Embeddable
data class SattPaaVent(
    val from: LocalDate,
    val to: LocalDate,
    val reason: String?,
    val sattPaaVentReasonId: String,
)