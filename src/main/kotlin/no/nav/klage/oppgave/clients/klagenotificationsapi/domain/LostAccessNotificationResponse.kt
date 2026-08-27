package no.nav.klage.oppgave.clients.klagenotificationsapi.domain

import java.util.UUID

data class LostAccessNotificationResponse(
    val behandlingId: UUID,
    val navIdent: String,
)
