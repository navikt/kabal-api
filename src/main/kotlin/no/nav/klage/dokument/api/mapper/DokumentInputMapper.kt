package no.nav.klage.dokument.api.mapper

import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.OpplastetMellomlagretDokument
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
        const val DEFAULT_FILENAME = "filnavn.pdf"
    }

    fun mapToMellomlagretDokument(
        multipartFile: MultipartFile
    ): MellomlagretDokument {
        if (multipartFile.originalFilename == null) {
            logger.warn("Er ikke noe filnavn i filen!")
        }
        return OpplastetMellomlagretDokument(
            title = multipartFile.originalFilename ?: DEFAULT_FILENAME,
            content = multipartFile.bytes,
            contentType = multipartFile.contentType?.let { MediaType.parseMediaType(it) }
                ?: MediaType.valueOf(Tika().detect(multipartFile.bytes))
        )
    }
}