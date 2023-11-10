package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling

interface AnkeITrygderettenbehandlingRepositoryCustom {
    fun getCompletedAnkeITrygderettenbehandlinger(partIdValue: String): List<AnkeITrygderettenbehandling>
}