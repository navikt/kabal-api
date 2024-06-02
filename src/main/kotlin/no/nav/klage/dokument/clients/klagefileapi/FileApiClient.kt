package no.nav.klage.dokument.clients.klagefileapi

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.nio.file.Files


@Component
class FileApiClient(
    private val fileWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getDocument(id: String, systemUser: Boolean = false): Resource {
        logger.debug("Fetching document with id {}", id)

        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        val dataBufferFlux = fileWebClient.get()
            .uri { it.path("/document/{id}").build(id) }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDocument.name, secureLogger)
            }
            .bodyToFlux(DataBuffer::class.java)

        val tempFile = Files.createTempFile(null, null)

        DataBufferUtils.write(dataBufferFlux, tempFile).block()
        return FileSystemResource(tempFile)
    }

    fun getDocumentAsSignedURL(id: String, systemUser: Boolean = false): String {
        logger.debug("Fetching document (signed URL) with id {}", id)

        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        return fileWebClient.get()
            .uri { it.path("/document/{id}/signedurl").build(id) }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDocument.name, secureLogger)
            }
            .bodyToMono<String>()
            .block()!!
    }

    fun deleteDocument(id: String, systemUser: Boolean = false) {
        logger.debug("Deleting document with id {}", id)

        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        try {
            val deletedInGCS = fileWebClient
                .delete()
                .uri { it.path("/document/{id}").build(id) }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::deleteDocument.name, secureLogger)
                }
                .bodyToMono<Boolean>()
                .block()

            if (deletedInGCS == true) {
                logger.debug("Document successfully deleted in file store.")
            } else {
                logger.warn("Could not successfully delete document in file store.")
            }
        } catch (e: Exception) {
            logger.error("Could not delete document ($id) from kabal-file-api", e)
        }
    }

    fun uploadDocument(resource: Resource, systemUser: Boolean = false): String {
        logger.debug("Uploading document to storage")


        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        var start = System.currentTimeMillis()
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", resource).contentType(MediaType.APPLICATION_PDF)
        logger.debug("File added to body. Time taken: ${System.currentTimeMillis() - start} ms")

        start = System.currentTimeMillis()
        val response = fileWebClient
            .post()
            .uri("/document")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::uploadDocument.name, secureLogger)
            }
            .bodyToMono<DocumentUploadedResponse>()
            .block()

        logger.debug("Response received. Time taken: ${System.currentTimeMillis() - start} ms")
        requireNotNull(response)

        if (resource is FileSystemResource) {
            start = System.currentTimeMillis()
            resource.file.delete()
            logger.debug("File deleted. Time taken: ${System.currentTimeMillis() - start} ms")
        }

        logger.debug("Document uploaded to file store with id: {}", response.id)
        return response.id
    }
}

data class DocumentUploadedResponse(val id: String)