package no.nav.klage.oppgave.clients.oppgaveapi

import java.time.LocalDate

abstract class UpdateOppgaveRequest(
    open val versjon: Int,
    open val endretAvEnhetsnr: String,
)

data class TildelOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Long?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class FradelOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class ReturnOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
    val mappeId: Long?,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr) {
    data class Kommentar(
        val tekst: String,
        val automatiskGenerert: Boolean,
    )
}

