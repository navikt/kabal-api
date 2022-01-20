package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.Behandling
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface BehandlingRepository : JpaRepository<Behandling, UUID> {

    fun findByMottakId(mottakId: UUID): Behandling?

}