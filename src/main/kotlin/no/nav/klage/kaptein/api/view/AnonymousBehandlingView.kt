package no.nav.klage.kaptein.api.view

import java.time.LocalDate
import java.util.*

data class AnonymousBehandlingListView(
    val anonymizedBehandlingList: List<AnonymousBehandlingView>,
    val total: Int,
)

data class AnonymousBehandlingView(
    val id: UUID,
    val tilknyttetEnhet: String?,
    val hjemmelIdList: List<String>,
    val registreringshjemmelIdList: List<String>,
    val avsluttetAvSaksbehandler: LocalDate?,
    val ytelseId: String,
    val utfallId: String?,
    val typeId: String,
    val mottattVedtaksinstans: LocalDate?,
    val vedtaksinstansEnhet: String,
//    val vedtaksinstansgruppe: Int,
    val mottattKlageinstans: LocalDate,
    val tilbakekreving: Boolean,
)
