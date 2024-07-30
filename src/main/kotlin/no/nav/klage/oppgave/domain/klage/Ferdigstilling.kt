package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class Ferdigstilling(
    var avsluttet: LocalDateTime? = null,
    val avsluttetAvSaksbehandler: LocalDateTime,
    val navIdent: String,
    val navn: String,
)