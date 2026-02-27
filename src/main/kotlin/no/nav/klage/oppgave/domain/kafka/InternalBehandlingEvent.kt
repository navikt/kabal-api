package no.nav.klage.oppgave.domain.kafka

import io.opentelemetry.api.trace.Span
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun currentTraceId(): String {
    return try {
        Span.current().spanContext.traceId
    } catch (_: Exception) {
        "unknown"
    }
}

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
    INCLUDED_DOCUMENTS_ADDED,
    INCLUDED_DOCUMENTS_REMOVED,
    INCLUDED_DOCUMENTS_CLEARED,
    //Change to SMART_DOCUMENT_LANGUAGE_CHANGED when FE is ready
    SMART_DOCUMENT_LANGUAGE,
    SMART_DOCUMENT_VERSIONED,
    SMART_DOCUMENT_COMMENT_ADDED,
    SMART_DOCUMENT_COMMENT_CHANGED,
    SMART_DOCUMENT_COMMENT_REMOVED,
    MESSAGE,
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
    FEILREGISTRERING,
    FERDIGSTILT,
    SATT_PAA_VENT,
    TILDELING,
    TILBAKEKREVING,
    GOSYSOPPGAVE,
    VARSLET_FRIST,
}

data class Employee(
    val navIdent: String,
    val navn: String,
)

data class JournalfoertDokument(
    val journalpostId: String,
    val dokumentInfoId: String,
)

abstract class BaseEvent(
    open val actor: Employee,
    open val timestamp: LocalDateTime,
    open val traceId: String,
)

data class MinimalEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class MedunderskriverEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val medunderskriver: Employee?,
    val flowState: FlowState,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class RolEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val rol: Employee?,
    val flowState: FlowState,
    val returnDate: LocalDateTime?,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class KlagerEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val part: Part,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class FullmektigEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val part: Part?,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class Part(
    val id: UUID,
    val identifikator: String?,
    val type: BehandlingDetaljerView.IdType?,
    val name: String?,
    val statusList: List<BehandlingDetaljerView.PartStatus>,
    val available: Boolean,
    val language: String?,
    val address: BehandlingDetaljerView.Address?,
    val utsendingskanal: BehandlingDetaljerView.Utsendingskanal
)

data class MeldingEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val id: String,
    val text: String,
    val notify: Boolean,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class UtfallEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val utfallId: String?,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class GosysoppgaveEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val gosysOppgave: GosysOppgaveView?,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class TilbakekrevingEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val tilbakekreving: Boolean,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class ExtraUtfallEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val utfallIdList: List<String>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class InnsendingshjemlerEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val hjemmelIdSet: Set<String>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class RegistreringshjemlerEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val hjemmelIdSet: Set<String>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class MottattVedtaksinstansEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val mottattVedtaksinstans: LocalDate,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class VarsletFristEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val varsletFrist: LocalDate,
    val timesPreviouslyExtended: Int,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class DocumentsChangedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val documents: List<DocumentChanged>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId) {
    data class DocumentChanged(
        val id: String,
        val parentId: String?,
        val dokumentTypeId: String?,
        val tittel: String,
        val isMarkertAvsluttet: Boolean,
    )
}

data class SmartDocumentChangedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val document: SmartDocumentChanged,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId) {
    data class SmartDocumentChanged(
        val id: String,
        val language: DokumentView.Language,
    )
}

data class DocumentFinishedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val id: String,
    val journalpostList: List<DokumentReferanse>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class DocumentsRemovedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val idList: List<String>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class DocumentsAddedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val documents: List<DokumentView>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class IncludedDocumentsChangedEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val journalfoertDokumentReferenceSet: Set<JournalfoertDokument>,
    override val traceId: String,
): BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class DocumentPatched(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val author: Employee,
    val version: Int,
    val documentId: String,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class CommentEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val author: Employee,
    val text: String,
    val commentId: String,
    val parentId: String?,
    val documentId: String,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class JournalfoertDocumentModified(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val journalpostId: String,
    val dokumentInfoId: String,
    val tittel: String,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class FeilregistreringEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val registered: LocalDateTime,
    val reason: String,
    val fagsystemId: String,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class BehandlingFerdigstiltEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val avsluttetAvSaksbehandlerDate: LocalDateTime,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)

data class SattPaaVentEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val sattPaaVent: SattPaaVent?,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId) {
    data class SattPaaVent(
        val from: LocalDate,
        val to: LocalDate,
        val reason: String?,
        val reasonId: String,
    )
}

data class TildelingEvent(
    override val actor: Employee,
    override val timestamp: LocalDateTime,
    val saksbehandler: Employee?,
    val fradelingReasonId: String?,
    val hjemmelIdList: List<String>,
    override val traceId: String,
) : BaseEvent(actor = actor, timestamp = timestamp, traceId = traceId)
