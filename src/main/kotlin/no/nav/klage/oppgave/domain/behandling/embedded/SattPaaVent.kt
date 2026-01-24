package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

@Embeddable
data class SattPaaVent(
    @Column(name = "from", nullable = false)
    val from: LocalDate,
    @Column(name = "to", nullable = false)
    val to: LocalDate,
    @Column(name = "reason")
    val reason: String?,
    @Column(name = "reason_id", nullable = false)
    val reasonId: String,
)