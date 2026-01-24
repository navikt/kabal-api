package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class Tildeling(
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "enhet")
    val enhet: String?,
    @Column(name = "tidspunkt", nullable = false)
    val tidspunkt: LocalDateTime,
)