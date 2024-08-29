package no.nav.klage.oppgave.clients.kaka.model.request

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class SaksdataInput(
    val sakenGjelder: String,
    val sakstype: String,
    val ytelseId: String,
    val mottattVedtaksinstans: LocalDate?,
    val vedtaksinstansEnhet: String?,
    val mottattKlageinstans: LocalDate,
    val utfall: String,
    val registreringshjemler: List<String>,
    val utfoerendeSaksbehandler: String,
    val tilknyttetEnhet: String,
    val kvalitetsvurderingId: UUID?,
    val avsluttetAvSaksbehandler: LocalDateTime,
)