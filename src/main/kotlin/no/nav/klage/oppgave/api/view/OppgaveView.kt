package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
    val previousSaksbehandler: SaksbehandlerView?,
    val datoSendtTilTR: LocalDate?,
) {

    data class SattPaaVent(
        val from: LocalDate,
        val to: LocalDate,
        val isExpired: Boolean,
        val reason: String,
    )
}

interface CommonOppgaverQueryParams {
    var typer: List<String>
    var ytelser: List<String>
    var hjemler: List<String>
    val rekkefoelge: Rekkefoelge
    val sortering: Sortering
}

interface FerdigstilteOppgaverQueryParams {
    val ferdigstiltFrom: LocalDate?
    val ferdigstiltTo: LocalDate?
    val registreringshjemler: List<String>
}

data class MineFerdigstilteOppgaverQueryParams(
    override var typer: List<String> = emptyList(),
    override var ytelser: List<String> = emptyList(),
    override var hjemler: List<String> = emptyList(),
    override var registreringshjemler: List<String> = emptyList(),
    override val rekkefoelge: Rekkefoelge = Rekkefoelge.STIGENDE,
    override val sortering: Sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
    override val ferdigstiltFrom: LocalDate?,
    override val ferdigstiltTo: LocalDate?,
) : CommonOppgaverQueryParams, FerdigstilteOppgaverQueryParams

data class EnhetensFerdigstilteOppgaverQueryParams(
    override var typer: List<String> = emptyList(),
    override var ytelser: List<String> = emptyList(),
    override var hjemler: List<String> = emptyList(),
    override var registreringshjemler: List<String> = emptyList(),
    override val rekkefoelge: Rekkefoelge = Rekkefoelge.STIGENDE,
    override val sortering: Sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
    override val ferdigstiltFrom: LocalDate?,
    override val ferdigstiltTo: LocalDate?,
    var tildelteSaksbehandlere: List<String> = emptyList(),
) : CommonOppgaverQueryParams, FerdigstilteOppgaverQueryParams

enum class Rekkefoelge {
    STIGENDE, SYNKENDE
}

enum class Sortering {
    FRIST, MOTTATT, ALDER, PAA_VENT_FROM, PAA_VENT_TO, AVSLUTTET_AV_SAKSBEHANDLER, RETURNERT_FRA_ROL
}

data class BehandlingerListResponse(
    val antallTreffTotalt: Int,
    val behandlinger: List<UUID>,
)