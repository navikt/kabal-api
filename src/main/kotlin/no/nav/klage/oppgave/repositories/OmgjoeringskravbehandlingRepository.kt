package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Omgjoeringskravbehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface OmgjoeringskravbehandlingRepository : JpaRepository<Omgjoeringskravbehandling, UUID> {

    fun findByMottakId(mottakId: UUID): Omgjoeringskravbehandling?
}