package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime

data class GosysOppgaveView(
    val id: Long,
    val tildeltEnhetsnr: String,
    val endretAvEnhetsnr: String?,
    val endretAv: String?,
    val endretTidspunkt: LocalDateTime?,
    val opprettetAv: String?,
    val opprettetTidspunkt: LocalDateTime?,
    val beskrivelse: String?,
    val temaId: String,
    //Må parses via kodeverk
    val gjelder: String?,
    //Må parses via kodeverk
    val oppgavetype: String?,
    val fristFerdigstillelse: LocalDate?,
    val ferdigstiltTidspunkt: LocalDateTime?,
    val status: Status,
    val editable: Boolean,
    val opprettetAvEnhet: EnhetView?,
    var alreadyUsed: Boolean,
) {
    enum class Status {
        OPPRETTET,
        AAPNET,
        UNDER_BEHANDLING,
        FERDIGSTILT,
        FEILREGISTRERT
    }
}