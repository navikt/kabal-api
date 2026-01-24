package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FagsystemConverter
import java.time.LocalDateTime

@Embeddable
data class Feilregistrering(
    @Column(name = "nav_ident", nullable = false)
    val navIdent: String,
    @Column(name = "navn")
    val navn: String?,
    @Column(name = "registered", nullable = false)
    val registered: LocalDateTime,
    @Column(name = "reason", nullable = false)
    val reason: String,
    @Column(name = "fagsystem", nullable = false)
    @Convert(converter = FagsystemConverter::class)
    val fagsystem: Fagsystem
)