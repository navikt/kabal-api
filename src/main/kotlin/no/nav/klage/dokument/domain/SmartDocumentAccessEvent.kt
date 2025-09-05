package no.nav.klage.dokument.domain

import no.nav.klage.oppgave.domain.behandling.Behandling
import java.util.*

data class SmartDocumentAccessBehandlingEvent(
    val behandling: Behandling,
)

data class SmartDocumentAccessDocumentEvent(
    val duaId: UUID,
)