package no.nav.klage.oppgave.api.view

import java.util.*

data class RelevantBehandlingerResponse(
    val aapneBehandlinger: List<UUID>,
    val paaVentBehandlinger: List<UUID>,
)