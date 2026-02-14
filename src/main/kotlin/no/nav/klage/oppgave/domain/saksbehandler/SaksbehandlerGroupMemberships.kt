package no.nav.klage.oppgave.domain.saksbehandler

import no.nav.klage.kodeverk.AzureGroup

data class SaksbehandlerGroupMemberships(
    val groups: List<AzureGroup>
)