package no.nav.klage.oppgave.domain.events

import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Klagebehandling

data class KlagebehandlingEndretEvent(
    val klagebehandling: Klagebehandling,
    val endringslogginnslag: List<Endringslogginnslag>
)