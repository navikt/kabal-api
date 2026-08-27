package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.events.AutomaticSvarbrevEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AutomaticSvarbrevEventRepository : JpaRepository<AutomaticSvarbrevEvent, UUID> {
    fun getAllByStatusInOrderByCreated(statuses: List<AutomaticSvarbrevEvent.AutomaticSvarbrevStatus>): List<AutomaticSvarbrevEvent>
}
