package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class MedunderskriverTildeling(
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String?,
    @Column(name = "tidspunkt")
    val tidspunkt: LocalDateTime
)