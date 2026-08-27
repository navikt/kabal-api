package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AnkeITrygderettenbehandlingRepository : JpaRepository<AnkeITrygderettenbehandling, UUID>
