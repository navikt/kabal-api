package no.nav.klage.oppgave.domain.events

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*


@Entity
@Table(name = "automatic_svarbrev_event", schema = "klage")
class AutomaticSvarbrevEvent (
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    var status: AutomaticSvarbrevStatus,
    @Column(name = "created")
    val created: LocalDateTime,
    @Column(name = "modified")
    var modified: LocalDateTime,
    @Column(name = "behandling_id")
    val behandlingId: UUID,
    @Column(name = "dokument_under_arbeid_id")
    var dokumentUnderArbeidId: UUID?,
    @Column(name = "receivers_are_set")
    var receiversAreSet: Boolean,
    @Column(name = "document_is_marked_as_finished")
    var documentIsMarkedAsFinished: Boolean,
    @Column(name = "varslet_frist_is_set_in_behandling")
    var varsletFristIsSetInBehandling: Boolean,
) {
    enum class AutomaticSvarbrevStatus {
        NOT_HANDLED,
        HANDLED
    }

    override fun toString(): String {
        return "AutomaticSvarbrevEvent(id=$id, status=$status, created=$created, modified=$modified, behandlingId=$behandlingId, dokumentUnderArbeidId=$dokumentUnderArbeidId, receiversAreSet=$receiversAreSet, documentIsMarkedAsFinished=$documentIsMarkedAsFinished, varsletFristIsSetInBehandling=$varsletFristIsSetInBehandling)"
    }
}