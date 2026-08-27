package no.nav.klage.oppgave.domain.events

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "automatic_svarbrev_event", schema = "klage")
class AutomaticSvarbrevEvent(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: AutomaticSvarbrevStatus,
    @Column(name = "created", nullable = false)
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified", nullable = false)
    var modified: LocalDateTime = created,
    @Column(name = "behandling_id", nullable = false)
    val behandlingId: UUID,
    @Column(name = "dokument_under_arbeid_id")
    var dokumentUnderArbeidId: UUID?,
    @Column(name = "receivers_are_set", nullable = false)
    var receiversAreSet: Boolean,
    @Column(name = "document_is_marked_as_finished", nullable = false)
    var documentIsMarkedAsFinished: Boolean,
    @Column(name = "varslet_frist_is_set_in_behandling", nullable = false)
    var varsletFristIsSetInBehandling: Boolean,
) {
    enum class AutomaticSvarbrevStatus {
        NOT_HANDLED,
        HANDLED,
    }

    override fun toString(): String =
        "AutomaticSvarbrevEvent(id=$id, status=$status, created=$created, modified=$modified, behandlingId=$behandlingId, dokumentUnderArbeidId=$dokumentUnderArbeidId, receiversAreSet=$receiversAreSet, documentIsMarkedAsFinished=$documentIsMarkedAsFinished, varsletFristIsSetInBehandling=$varsletFristIsSetInBehandling)"
}
