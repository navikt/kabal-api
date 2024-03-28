package no.nav.klage.dokument.clients.kabaljsontopdf

import no.nav.klage.dokument.clients.kabaljsontopdf.domain.DocumentValidationResponse
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.SvarbrevRequest
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono


@Component
class KabalJsonToPdfClient(
    private val kabalJsonToPdfWebClient: WebClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getPDFDocument(json: String): PDFDocument {
        logger.debug("Getting pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/topdf").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getPDFDocument.name, secureLogger)
            }
            .toEntity(ByteArray::class.java)
            .map {
                val filename = it.headers["filename"]?.first()
                PDFDocument(
                    filename = filename
                        ?: "somefilename",
                    bytes = it.body ?: throw RuntimeException("Could not get PDF data")
                )
            }
            .block() ?: throw RuntimeException("PDF response was null")
    }

    fun getInnholdsfortegnelse(innholdsfortegnelseRequest: InnholdsfortegnelseRequest): PDFDocument {
        logger.debug("Getting innholdsfortegnelse from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/toinnholdsfortegnelse").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(innholdsfortegnelseRequest)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getInnholdsfortegnelse.name, secureLogger)
            }
            .toEntity(ByteArray::class.java)
            .map {
                val filename = it.headers["filename"]?.first()
                PDFDocument(
                    filename = filename
                        ?: "somefilename",
                    bytes = it.body ?: throw RuntimeException("Could not get PDF data")
                )
            }
            .block() ?: throw RuntimeException("PDF response was null")
    }

    fun validateJsonDocument(json: String): DocumentValidationResponse {
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/validate").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::validateJsonDocument.name, secureLogger)
            }
            .bodyToMono<DocumentValidationResponse>()
            .block() ?: throw RuntimeException("Response null")
    }

    fun getSvarbrevPDF(svarbrevRequest: SvarbrevRequest): ByteArray {
        logger.debug("Getting pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/svarbrev").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(svarbrevRequest)
            .retrieve()
            .bodyToMono<ByteArray>()
            .block() ?: throw RuntimeException("PDF response was null")
    }
}