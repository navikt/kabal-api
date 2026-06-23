package no.nav.klage.oppgave.clients.gosysoppgave

import java.time.LocalDate

abstract class UpdateOppgaveRequest(
    open val versjon: Int,
    open val endretAvEnhetsnr: String?,
)

data class TildelGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Long?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class FradelGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val tilordnetRessurs: String?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateGosysOppgaveOnCompletedBehandlingRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val fristFerdigstillelse: LocalDate,
    val mappeId: Long?,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateFristInGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val fristFerdigstillelse: LocalDate,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class Kommentar(
    val tekst: String,
    val automatiskGenerert: Boolean,
)

data class AddKommentarToGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class AvsluttGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val status: Status,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)