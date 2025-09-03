package no.nav.klage.kaptein.api.view

import no.nav.klage.oppgave.domain.behandling.embedded.SattPaaVent
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
    val mottattVedtaksinstans: LocalDate?,
    val temaId: String,
    val ytelseId: String,
    val typeId: String,
    val mottattKlageinstans: LocalDate,
    val avsluttetAvSaksbehandlerDate: LocalDate?,
    val isAvsluttetAvSaksbehandler: Boolean,
    val isTildelt: Boolean,
    val tildeltEnhet: String?,
    val frist: LocalDate?,
    val ageKA: Int,
    val datoSendtMedunderskriver: LocalDate?,
    val hjemmelIdList: List<String>,
    val modified: LocalDateTime,
    val created: LocalDateTime,
    val resultat: VedtakView?,
    val sattPaaVent: SattPaaVent?,
    val sendtTilTrygderetten: LocalDateTime?,
    val kjennelseMottatt: LocalDateTime?,
    val feilregistrering: FeilregistreringView?,
    val fagsystemId: String,
    val varsletFrist: LocalDate?,
    val tilbakekreving: Boolean,
    val timesPreviouslyExtended: Int,
) {
    data class VedtakView(
        val id: UUID,
        val utfallId: String?,
        val hjemmelIdSet: Set<String>,
    )

    data class FeilregistreringView(
        val registered: LocalDateTime,
        val reason: String,
        val fagsystemId: String,
    )
}
