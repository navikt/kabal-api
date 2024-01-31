package no.nav.klage.oppgave.domain.kafka

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "kafka_event", schema = "klage")
class KafkaEvent(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "behandling_id")
    val behandlingId: UUID,
    @Column(name = "kilde_referanse")
    val kildeReferanse: String,
    @Column(name = "kilde")
    val kilde: String,
    @Column(name = "status_id")
    @Enumerated(EnumType.STRING)
    var status: UtsendingStatus = UtsendingStatus.IKKE_SENDT,
    @Column(name = "json_payload")
    var jsonPayload: String,
    @Column(name = "error_message")
    var errorMessage: String? = null,
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    val type: EventType
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KafkaEvent

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "KafkaEvent(id=$id, behandlingId=$behandlingId, kildeReferanse='$kildeReferanse', kilde='$kilde', status=$status, jsonPayload='$jsonPayload', errorMessage=$errorMessage, created=$created, type=$type)"
    }

}
