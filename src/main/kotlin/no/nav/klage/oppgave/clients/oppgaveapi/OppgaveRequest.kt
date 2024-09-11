package no.nav.klage.oppgave.clients.oppgaveapi

abstract class UpdateOppgaveRequest(
    open val versjon: Int,
    open val endretAvEnhetsnr: String,
)

data class TildelOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class FradelOppgaveInput(
    override val versjon: Int,
    override val endretAvEnhetsnr: String,
    val tilordnetRessurs: String?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)
