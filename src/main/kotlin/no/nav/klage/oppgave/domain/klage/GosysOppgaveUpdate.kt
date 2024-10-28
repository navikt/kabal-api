package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class GosysOppgaveUpdate(
    @Column(name = "oppgave_returned_tildelt_enhetsnummer")
    val oppgaveUpdateTildeltEnhetsnummer: String,
    @Column(name = "oppgave_returned_mappe_id")
    val oppgaveUpdateMappeId: Long?,
    @Column(name = "oppgave_returned_kommentar")
    val oppgaveUpdateKommentar: String,
)