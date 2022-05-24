package no.nav.klage.dokument.api.mapper

import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.OpplastetMellomlagretDokument
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.util.getLogger
import org.apache.tika.Tika
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class DokumentInputMapper {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun mapToMellomlagretDokument(
        multipartFile: MultipartFile,
        tittel: String?,
        dokumentType: DokumentType
    ): MellomlagretDokument {
        val dokumentTittel = tittel ?: multipartFile.originalFilename
        ?: (dokumentType.defaultFilnavn.also { logger.warn("Filnavn ikke angitt i MultipartFile") })
        return OpplastetMellomlagretDokument(
            title = dokumentTittel,
            content = multipartFile.bytes,
            contentType = multipartFile.contentType?.let { MediaType.parseMediaType(it) }
                ?: MediaType.valueOf(Tika().detect(multipartFile.bytes))
        )
    }
}