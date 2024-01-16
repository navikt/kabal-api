package no.nav.klage.oppgave.domain.kafka

import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.DokumentReferanse
import java.time.LocalDate
import java.time.LocalDateTime

data class InternalBehandlingEvent(
    val behandlingId: String,
    val type: InternalEventType,
    val data: String,
)

data class InternalIdentityEvent(
    val identifikator: String,
    val type: InternalEventType,
    val data: String,
)

enum class InternalEventType {
    DOCUMENT_FINISHED,
    DOCUMENTS_ADDED,
    DOCUMENTS_REMOVED,
    DOCUMENTS_CHANGED,
    MESSAGE, // Polling
    ROL, // Polling
    MEDUNDERSKRIVER, // Polling
    KLAGER,
    FULLMEKTIG,
    UTFALL,
    EXTRA_UTFALL,
    INNSENDINGSHJEMLER,
    REGISTRERINGSHJEMLER,
    MOTTATT_VEDTAKSINSTANS,
    JOURNALFOERT_DOCUMENT_MODIFIED,
}

abstract class BaseEvent(
    open val actor: Actor,
    open val timestamp: LocalDateTime,
) {
    data class Actor(
        val navIdent: String,
        val name: String,
    )
}

data class MedunderskriverEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val navIdent: String?,
    val name: String?,
    val flowState: FlowState,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class RolEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val navIdent: String?,
    val name: String?,
    val flowState: FlowState,
    val returnDate: LocalDateTime?,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class KlagerEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val part: Part,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class FullmektigEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val part: Part?,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class Part(
    val id: String,
    val type: BehandlingDetaljerView.IdType,
    val name: String?,
    val statusList: List<BehandlingDetaljerView.PartStatus>,
)

data class MeldingEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val id: String,
    val text: String,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class UtfallEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val utfallId: String?,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class ExtraUtfallEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val utfallIdList: List<String>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class InnsendingshjemlerEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val hjemmelIdSet: Set<String>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class RegistreringshjemlerEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val hjemmelIdSet: Set<String>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class MottattVedtaksinstansEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val mottattVedtaksinstans: LocalDate,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class DocumentsChangedEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val documents: List<DocumentChanged>,
) : BaseEvent(actor = actor, timestamp = timestamp) {
    data class DocumentChanged(
        val id: String,
        val parentId: String?,
        val dokumentTypeId: String?,
        val tittel: String,
        val isMarkertAvsluttet: Boolean,
    )
}

data class DocumentFinishedEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val id: String,
    val journalpostList: List<DokumentReferanse>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class DocumentsRemovedEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val idList: List<String>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class DocumentsAddedEvent(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val documents: List<DokumentView>,
) : BaseEvent(actor = actor, timestamp = timestamp)

data class JournalfoertDocumentModified(
    override val actor: Actor,
    override val timestamp: LocalDateTime,
    val journalpostId: String,
    val dokumentInfoId: String,
    val tittel: String,
) : BaseEvent(actor = actor, timestamp = timestamp)
