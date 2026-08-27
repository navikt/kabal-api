package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.TaskListMerkantil
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TaskListMerkantilRepository : JpaRepository<TaskListMerkantil, UUID> {
    fun findByBehandlingId(behandlingId: UUID): Optional<TaskListMerkantil>
}
