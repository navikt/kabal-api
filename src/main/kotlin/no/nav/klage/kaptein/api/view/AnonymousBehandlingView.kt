package no.nav.klage.kaptein.api.view

import no.nav.klage.oppgave.domain.klage.SattPaaVent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class AnonymousBehandlingListView(
    val anonymizedBehandlingList: List<AnonymousBehandlingView>,
    val total: Int,
)

data class AnonymousBehandlingView(
    val id: UUID,
    val fraNAVEnhet: String?,
    val mottattVedtaksinstans: LocalDate? = null,
    val temaId: String,
    val ytelseId: String,
    val typeId: String,
    val mottattKlageinstans: LocalDate,
    val avsluttetAvSaksbehandlerDate: LocalDate?,
    val isAvsluttetAvSaksbehandler: Boolean,
    val frist: LocalDate? = null,
    val datoSendtMedunderskriver: LocalDate?,
    val hjemmelIdList: List<String>,
    val modified: LocalDateTime,
    val created: LocalDateTime,
    val resultat: VedtakView?,
    val sattPaaVent: SattPaaVent? = null,
    val sendtTilTrygderetten: LocalDateTime? = null,
    val kjennelseMottatt: LocalDateTime? = null,
    val feilregistrering: FeilregistreringView? = null,
    val fagsystemId: String,
    val varsletFrist: LocalDate?,
    val tilbakekreving: Boolean,
    val timesPreviouslyExtended: Int,
) {
    data class VedtakView(
        val id: UUID,
        val utfallId: String?,
        val extraUtfallIdSet: Set<String>,
        val hjemmelIdSet: Set<String>,
    )

    data class FeilregistreringView(
        val registered: LocalDateTime,
        val reason: String,
        val fagsystemId: String,
    )
}
