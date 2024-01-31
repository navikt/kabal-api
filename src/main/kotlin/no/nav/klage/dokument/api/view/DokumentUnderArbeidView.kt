package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.databind.JsonNode
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDate
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
    val content: JsonNode?,
    val version: Int?,
    val isMarkertAvsluttet: Boolean,
    val parentId: UUID?,
    val journalfoertDokumentReference: JournalfoertDokumentReference?,
    val creator: Creator,
    val creatorIdent: String,
    val creatorRole: BehandlingRole,
    val avsender: BehandlingDetaljerView.PartView?,
    val mottakerList: List<BehandlingDetaljerView.PartView>?,
    val inngaaendeKanal: InngaaendeKanal?,
    val datoMottatt: LocalDate?,
) {

    data class Creator(
        val employee: SaksbehandlerView,
        val creatorRole: BehandlingRole,
    )

    data class JournalfoertDokumentReference(
        val journalpostId: String,
        val dokumentInfoId: String,
        val harTilgangTilArkivvariant: Boolean,
        val datoOpprettet: LocalDateTime,
        val sortKey: String,
    )
}

data class DokumentViewWithList(
    val modified: LocalDateTime?,
    val alteredDocuments: List<NewParent>,
    val duplicateJournalfoerteDokumenter: List<UUID>,
)

data class DocumentModified(
    val modified: LocalDateTime,
)

data class SmartDocumentModified(
    val modified: LocalDateTime,
    val version: Int,
)

data class NewParent(
    val id: UUID,
    val parentId: UUID,
    val modified: LocalDateTime,
)