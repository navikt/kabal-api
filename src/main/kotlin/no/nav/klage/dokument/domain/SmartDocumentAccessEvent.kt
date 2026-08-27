package no.nav.klage.dokument.domain

import no.nav.klage.oppgave.domain.behandling.Behandling
import java.util.UUID

data class SmartDocumentAccessBehandlingEvent(
    val behandling: Behandling,
)

data class SmartDocumentDeletedEvent(
    val duaId: UUID,
)

data class SmartDocumentMarkedAsFinishedEvent(
    val duaId: UUID,
)
