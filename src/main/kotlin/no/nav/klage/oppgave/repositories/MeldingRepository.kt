package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.Melding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface MeldingRepository : JpaRepository<Melding, UUID> {

    fun findByBehandlingIdOrderByCreatedDesc(behandlingId: UUID): List<Melding>

}