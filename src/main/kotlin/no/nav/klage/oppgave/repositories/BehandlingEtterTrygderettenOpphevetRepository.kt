package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.klage.BehandlingEtterTrygderettenOpphevet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BehandlingEtterTrygderettenOpphevetRepository : JpaRepository<BehandlingEtterTrygderettenOpphevet, UUID> {
}