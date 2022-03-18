package no.nav.klage.oppgave.api.view

import java.util.*

data class AnkeBasertPaaKlageInput (
    val klagebehandlingId: UUID,
    val innsendtAnkeJournalpostId: String?
)
