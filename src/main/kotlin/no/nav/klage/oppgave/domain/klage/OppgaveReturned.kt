package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class OppgaveReturned(
    @Column(name = "oppgave_returned_tildelt_enhetsnummer")
    val oppgaveReturnedTildeltEnhetsnummer: String,
    @Column(name = "oppgave_returned_mappe_id")
    val oppgaveReturnedMappeId: Long?,
    @Column(name = "oppgave_returned_kommentar")
    val oppgaveReturnedKommentar: String,
)