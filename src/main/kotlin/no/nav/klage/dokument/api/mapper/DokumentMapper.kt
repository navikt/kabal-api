package no.nav.klage.dokument.api.mapper

import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.HovedDokumentView
import no.nav.klage.dokument.api.view.VedleggView
import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.HovedDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Vedlegg
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

    fun mapToHovedDokumentView(hovedDokument: HovedDokument): HovedDokumentView {
        return HovedDokumentView(
            id = hovedDokument.persistentDokumentId.persistentDokumentId,
            tittel = hovedDokument.name,
            dokumentTypeId = hovedDokument.dokumentType.id,
            opplastet = hovedDokument.opplastet,
            isSmartDokument = hovedDokument.smartEditorId != null,
            isMarkertAvsluttet = hovedDokument.markertFerdig != null,
            vedlegg = hovedDokument.vedlegg.map { mapToVedleggView(it) }
        )
    }

    fun mapToVedleggView(vedlegg: Vedlegg): VedleggView {
        return VedleggView(
            id = vedlegg.persistentDokumentId.persistentDokumentId,
            tittel = vedlegg.name,
            dokumentTypeId = vedlegg.dokumentType.id,
            opplastet = vedlegg.opplastet,
            isSmartDokument = vedlegg.smartEditorId != null,
            isMarkertAvsluttet = vedlegg.markertFerdig != null,
        )
    }

    fun mapToDokumentView(dokumentUnderArbeid: DokumentUnderArbeid): DokumentView {
        return VedleggView(
            id = dokumentUnderArbeid.persistentDokumentId.persistentDokumentId,
            tittel = dokumentUnderArbeid.name,
            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
            opplastet = dokumentUnderArbeid.opplastet,
            isSmartDokument = dokumentUnderArbeid.smartEditorId != null,
            isMarkertAvsluttet = dokumentUnderArbeid.markertFerdig != null,
        )
    }
}