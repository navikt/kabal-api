package no.nav.klage.oppgave.clients.gosysoppgave

import java.time.LocalDate

abstract class UpdateOppgaveRequest(
    open val versjon: Int,
    open val endretAvEnhetsnr: String,
)

data class TildelGosysOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Long?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class FradelGosysOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateGosysOppgaveOnCompletedBehandlingInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
    val mappeId: Long?,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateFristInGosysOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class Kommentar(
    val tekst: String,
    val automatiskGenerert: Boolean,
)

data class AddKommentarToGosysOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class AvsluttGosysOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val status: Status,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)