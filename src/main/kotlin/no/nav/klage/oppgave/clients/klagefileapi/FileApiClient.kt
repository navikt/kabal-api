package no.nav.klage.oppgave.clients.klagefileapi

import brave.Tracer
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class FileApiClient(
    private val fileWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getDocument(id: String, systemUser: Boolean = false): ByteArray {
        logger.debug("Fetching document with id {}", id)

        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        return this.fileWebClient.get()
            .uri { it.path("/document/{id}").build(id) }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<ByteArray>()
            .block() ?: throw RuntimeException("Document could not be fetched")
    }

    fun deleteDocument(id: String, systemUser: Boolean = false) {
        logger.debug("Deleting document with id {}", id)

        val token = if (systemUser) {
            tokenUtil.getAppAccessTokenWithKabalFileApiScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithKabalFileApiScope()
        }

        val deletedInGCS = fileWebClient
            .delete()
            .uri("/document/$id")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<Boolean>()
            .block()

        if (deletedInGCS == true) {
            logger.debug("Document successfully deleted in file store.")
        } else {
            logger.warn("Could not successfully delete document in file store.")
        }
    }
}