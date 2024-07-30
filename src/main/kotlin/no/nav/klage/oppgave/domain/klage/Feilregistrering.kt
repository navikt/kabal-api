package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import no.nav.klage.kodeverk.Fagsystem
import java.time.LocalDateTime

@Embeddable
data class Feilregistrering(
    val navIdent: String,
    val navn: String?,
    val registered: LocalDateTime,
    val reason: String,
    @Convert(converter = FagsystemConverter::class)
    val fagsystem: Fagsystem
)