package no.nav.klage.oppgave.domain.kafka

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "kafka_event", schema = "klage")
class KafkaEvent(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "behandling_id", nullable = false)
    val behandlingId: UUID,
    @Column(name = "kilde_referanse", nullable = false)
    val kildeReferanse: String,
    @Column(name = "kilde", nullable = false)
    val kilde: String,
    @Column(name = "status_id", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: UtsendingStatus = UtsendingStatus.IKKE_SENDT,
    @Column(name = "json_payload", nullable = false)
    var jsonPayload: String,
    @Column(name = "error_message")
    var errorMessage: String? = null,
    @Column(name = "created", nullable = false)
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    val type: EventType,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KafkaEvent

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "KafkaEvent(id=$id, behandlingId=$behandlingId, kildeReferanse='$kildeReferanse', kilde='$kilde', status=$status, jsonPayload='$jsonPayload', errorMessage=$errorMessage, created=$created, type=$type)"
}
