package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime

data class OppgaveView(
    val id: String,
    val typeId: String,
    val ytelseId: String,
    val hjemmelIdList: List<String>,
    val registreringshjemmelIdList: List<String>,
    val frist: LocalDate?,
    val mottatt: LocalDate,
    val medunderskriver: BehandlingDetaljerView.CombinedMedunderskriverAndROLView,
    val rol: BehandlingDetaljerView.CombinedMedunderskriverAndROLView,
    val utfallId: String?,
    val avsluttetAvSaksbehandlerDate: LocalDate?,
    val isAvsluttetAvSaksbehandler: Boolean,
    val tildeltSaksbehandlerident: String?,
    val ageKA: Int,
    val sattPaaVent: SattPaaVent?,
    val feilregistrert: LocalDateTime?,
    val fagsystemId: String,
    val saksnummer: String,
) {

    data class SattPaaVent(
        val from: LocalDate,
        val to: LocalDate,
        val isExpired: Boolean,
        val reason: String,
    )
}