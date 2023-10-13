package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.databind.JsonNode
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*

data class JournalfoerteDokumenterResponse(
    val addedJournalfoerteDokumenter: List<DokumentView>,
    val duplicateJournalfoerteDokumenter: List<JournalfoertDokumentReference>,
)

data class DokumentView(
    val id: UUID,
    val tittel: String,
    val dokumentTypeId: String?,
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val type: DokumentUnderArbeid.DokumentUnderArbeidType,
    val isSmartDokument: Boolean,
    val templateId: String?,
    val isMarkertAvsluttet: Boolean,
    @Deprecated("use parentId")
    val parent: UUID?,
    val parentId: UUID?,
    val journalfoertDokumentReference: JournalfoertDokumentReference?,
    val creatorIdent: String,
    val creatorRole: BehandlingRole,
) {
    data class JournalfoertDokumentReference(
        val journalpostId: String,
        val dokumentInfoId: String,
        val harTilgangTilArkivvariant: Boolean,
        val datoOpprettet: LocalDateTime,
    )
}

data class DokumentViewWithList(
    val id: UUID?,
    val tittel: String?,
    val dokumentTypeId: String?,
    val created: LocalDateTime?,
    val modified: LocalDateTime?,
    val type: DokumentUnderArbeid.DokumentUnderArbeidType?,
    val isSmartDokument: Boolean?,
    val templateId: String?,
    val isMarkertAvsluttet: Boolean?,
    //Deprecated
    val parent: UUID?,
    val parentId: UUID?,
    val journalfoertDokumentReference: DokumentView.JournalfoertDokumentReference?,

    //TODO: Keep these two lists when FE uses new version
    val alteredDocuments: List<DokumentView>,
    val duplicateJournalfoerteDokumenter: List<DokumentView>,
)

data class SmartEditorDocumentView(
    val id: UUID,
    val tittel: String,
    val content: JsonNode,
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val templateId: String?,
    val dokumentTypeId: String,
    //Deprecated
    val parent: UUID?,
    val parentId: UUID?,
    val creatorIdent: String,
    val creatorRole: BehandlingRole,
)