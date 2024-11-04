package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class GosysOppgaveView(
    val id: Long,
    val tildeltEnhetsnr: String,
    val endretAvEnhetsnr: String?,
    val endretAv: SaksbehandlerView?,
    val endretTidspunkt: LocalDateTime?,
    val opprettetAv: SaksbehandlerView?,
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
    val mappe: GosysOppgaveMappeView?,
    val editable: Boolean,
    val opprettetAvEnhet: EnhetView?,
    var alreadyUsedBy: UUID?,
) {
    enum class Status {
        OPPRETTET,
        AAPNET,
        UNDER_BEHANDLING,
        FERDIGSTILT,
        FEILREGISTRERT
    }
}