package no.nav.klage.oppgave.api.view

import java.util.UUID

data class ReferenceToMergedDocumentsResponse(
    val reference: UUID,
    val title: String,
)
