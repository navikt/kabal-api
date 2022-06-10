package no.nav.klage.dokument.api.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.SmartEditorDocumentView
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class DokumentMapper {

    fun mapToByteArray(mellomlagretDokument: MellomlagretDokument): ResponseEntity<ByteArray> =
        ResponseEntity(
            mellomlagretDokument.content,
            HttpHeaders().apply {
                contentType = mellomlagretDokument.contentType
                add("Content-Disposition", "inline; filename=${mellomlagretDokument.title}")
            },
            HttpStatus.OK
        )

    fun mapToDokumentView(dokumentUnderArbeid: DokumentUnderArbeid): DokumentView {
        return DokumentView(
            id = dokumentUnderArbeid.id.id,
            tittel = dokumentUnderArbeid.name,
            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
            opplastet = dokumentUnderArbeid.created,
            isSmartDokument = dokumentUnderArbeid.smartEditorId != null,
            templateId = dokumentUnderArbeid.smartEditorTemplateId,
            version = dokumentUnderArbeid.smartEditorVersion,
            isMarkertAvsluttet = dokumentUnderArbeid.markertFerdig != null,
            parent = dokumentUnderArbeid.parentId?.id,
        )
    }

    fun mapToSmartEditorDocumentView(
        dokumentUnderArbeid: DokumentUnderArbeid,
        smartEditorDocument: DocumentOutput,
    ): SmartEditorDocumentView {
        return SmartEditorDocumentView(
            id = dokumentUnderArbeid.id.id,
            tittel = dokumentUnderArbeid.name,
            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
            templateId = dokumentUnderArbeid.smartEditorTemplateId,
            version = dokumentUnderArbeid.smartEditorVersion,
            parent = dokumentUnderArbeid.parentId?.id,
            content = jacksonObjectMapper().readTree(smartEditorDocument.json),
            created = smartEditorDocument.created,
            modified = smartEditorDocument.modified,
        )
    }
}