package no.nav.klage.dokument.clients.kabaljsontopdf

import no.nav.klage.dokument.clients.kabaljsontopdf.domain.DocumentValidationResponse
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.ForlengetBehandlingstidRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.SvarbrevRequest
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.oppgave.util.getLogger
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
    }

    fun getPDFDocument(json: String): PDFDocument {
        logger.debug("Getting pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/topdf").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getPDFDocument.name,
                    classLogger = logger,
                )
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
            .uri { it.path("/toinnholdsfortegnelse/v2").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(innholdsfortegnelseRequest)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getInnholdsfortegnelse.name,
                    classLogger = logger,
                )
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
                logErrorResponse(
                    response = response,
                    functionName = ::validateJsonDocument.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<DocumentValidationResponse>()
            .block() ?: throw RuntimeException("Response null")
    }

    fun getSvarbrevPDF(svarbrevRequest: SvarbrevRequest): ByteArray {
        logger.debug("Getting svarbrev pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/svarbrev").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(svarbrevRequest)
            .retrieve()
            .bodyToMono<ByteArray>()
            .block() ?: throw RuntimeException("PDF response was null")
    }

    fun getForlengetBehandlingstidPDF(forlengetBehandlingstidRequest: ForlengetBehandlingstidRequest): ByteArray {
        logger.debug("Getting forlenget behandlingstid pdf document from kabalJsontoPdf.")
        return kabalJsonToPdfWebClient.post()
            .uri { it.path("/forlengetbehandlingstid").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(forlengetBehandlingstidRequest)
            .retrieve()
            .bodyToMono<ByteArray>()
            .block() ?: throw RuntimeException("PDF response was null")
    }
}