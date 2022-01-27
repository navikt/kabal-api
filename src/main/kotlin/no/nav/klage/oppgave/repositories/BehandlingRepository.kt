package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.Behandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.*
import javax.persistence.LockModeType

interface BehandlingRepository : JpaRepository<Behandling, UUID> {

    fun findByMottakId(mottakId: UUID): Behandling?

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    override fun getOne(id: UUID): Behandling
}