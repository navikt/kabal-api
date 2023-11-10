package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Ankebehandling

interface AnkebehandlingRepositoryCustom {
    fun getCompletedAnkebehandlinger(partIdValue: String): List<Ankebehandling>
}