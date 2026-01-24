package no.nav.klage.oppgave.domain.behandling.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class GosysOppgaveUpdate(
    @Column(name = "oppgave_returned_tildelt_enhetsnummer", nullable = false)
    val oppgaveUpdateTildeltEnhetsnummer: String,
    @Column(name = "oppgave_returned_mappe_id")
    val oppgaveUpdateMappeId: Long?,
    @Column(name = "oppgave_returned_kommentar", nullable = false)
    val oppgaveUpdateKommentar: String,
)