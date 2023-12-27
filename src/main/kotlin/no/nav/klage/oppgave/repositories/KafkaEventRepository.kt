package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface KafkaEventRepository : JpaRepository<KafkaEvent, UUID> {
    fun getAllByStatusInAndTypeOrderByCreated(statuses: List<UtsendingStatus>, type: EventType): List<KafkaEvent>

    fun findByType(type: EventType): List<KafkaEvent>
}
