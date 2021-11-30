package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Tema
import no.nav.klage.kodeverk.Utfall
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class MuligAnke (
    val internalSaksnummer: UUID,
    val tema: Tema,
    val utfall: Utfall,
    val innsendtDate: LocalDate,
    val vedtakDate: LocalDateTime,
    val foedselsnummer: String
)
