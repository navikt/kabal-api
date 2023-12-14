package no.nav.klage.oppgave.api.view

import java.util.*

data class RelevantBehandlingerResponse(
    val behandlingIdList: List<UUID>
)