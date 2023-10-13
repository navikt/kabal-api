package no.nav.klage.dokument.clients.kabaljsontopdf

import io.micrometer.tracing.Tracer
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.DocumentValidationResponse
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono


@Component
class KabalJsonToPdfClient(
    private val kabalJsonToPdfWebClient: WebClient,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getPDFDocument(json: String): PDFDocument {
        logger.debug("Getting pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/topdf").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .retrieve()
            .toEntity(ByteArray::class.java)
            .map {
                val filename = it.headers["filename"]?.first()
                PDFDocument(
                    filename = filename
                        ?: "somefilename",
                    bytes = it.body ?: throw RuntimeException("Could not get PDF data")
                )
            }
            .block() ?: throw RuntimeException("PDF could not be created")
    }

    fun getInnholdsfortegnelse(innholdsfortegnelseRequest: InnholdsfortegnelseRequest): PDFDocument {
        logger.debug("Getting innholdsfortegnelse from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/toinnholdsfortegnelse").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(innholdsfortegnelseRequest)
            .retrieve()
            .toEntity(ByteArray::class.java)
            .map {
                val filename = it.headers["filename"]?.first()
                PDFDocument(
                    filename = filename
                        ?: "somefilename",
                    bytes = it.body ?: throw RuntimeException("Could not get PDF data")
                )
            }
            .block() ?: throw RuntimeException("PDF could not be created")
    }

    fun validateJsonDocument(json: String): DocumentValidationResponse {
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/validate").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .retrieve()
            .bodyToMono<DocumentValidationResponse>()
            .block() ?: throw RuntimeException("Response null")
    }
}