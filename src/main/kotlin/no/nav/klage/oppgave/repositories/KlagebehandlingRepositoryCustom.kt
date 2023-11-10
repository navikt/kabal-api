package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Klagebehandling


interface KlagebehandlingRepositoryCustom {

    fun getCompletedKlagebehandlinger(partIdValue: String): List<Klagebehandling>
}