package no.nav.klage.oppgave.clients.oppgaveapi

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateOppgaveInput(
    val versjon: Int,
    val fristFerdigstillelse: LocalDate?,
    val mappeId: Long?,
    val endretAvEnhetsnr: String?,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String?,
    val personident: String?,
    val beskrivelse: String?,
    val kommentar: Kommentar?,
) {
    data class Kommentar(
        val tekst: String,
        val automatiskGenerert: Boolean,
    )
}
