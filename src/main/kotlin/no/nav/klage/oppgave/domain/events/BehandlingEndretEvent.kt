package no.nav.klage.oppgave.domain.events

import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Endringsinnslag

data class BehandlingEndretEvent(
    val behandling: Behandling,
    val endringsinnslag: List<Endringsinnslag>
)