package no.nav.klage.oppgave.clients.klagenotificationsapi.domain

import java.util.*

data class LostAccessNotificationResponse(
    val behandlingId: UUID,
    val navIdent: String,
)