package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class Ferdigstilling(
    @Column(name = "avsluttet")
    var avsluttet: LocalDateTime? = null,
    @Column(name = "avsluttet_av_saksbehandler", nullable = false)
    val avsluttetAvSaksbehandler: LocalDateTime,
    @Column(name = "nav_ident", nullable = false)
    val navIdent: String,
    @Column(name = "navn", nullable = false)
    val navn: String,
)