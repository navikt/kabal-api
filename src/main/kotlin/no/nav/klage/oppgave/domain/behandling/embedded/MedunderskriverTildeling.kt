package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class MedunderskriverTildeling(
    val saksbehandlerident: String?,
    val tidspunkt: LocalDateTime
)