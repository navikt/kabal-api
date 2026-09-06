package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AnkeITrygderettenbehandlingFoer2027Repository : JpaRepository<AnkeITrygderettenbehandlingFoer2027, UUID>
