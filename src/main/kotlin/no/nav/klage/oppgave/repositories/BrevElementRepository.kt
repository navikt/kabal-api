package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.vedtaksbrev.BrevElement
import no.nav.klage.oppgave.domain.vedtaksbrev.enums.BrevElementKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BrevElementRepository : JpaRepository<BrevElement, UUID> {
    fun findByBrevIdAndKey(brevId: UUID, key: BrevElementKey): BrevElement
}


