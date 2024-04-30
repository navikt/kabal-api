package no.nav.klage.oppgave.clients.dokarkiv

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class DokarkivClient(
    private val dokarkivWebClient: WebClient,
    private val tokenUtil: TokenUtil
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun updateDocumentTitlesOnBehalfOf(journalpostId: String, input: UpdateDocumentTitlesJournalpostInput) {
        try {
            dokarkivWebClient.put()
                .uri("/journalpost/${journalpostId}")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope()}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(input)
                .retrieve()
                .bodyToMono(UpdateJournalpostResponse::class.java)
                .block()
                ?: throw RuntimeException("Journalpost could not be updated.")
        } catch (e: Exception) {
            logger.error("Error updating journalpost $journalpostId:", e)
        }

        logger.debug("Documents from journalpost $journalpostId were successfully updated.")
    }

    fun setLogiskeVedleggOnBehalfOf(
        dokumentInfoId: String,
        payload: SetLogiskeVedleggPayload,
    ) {
        try {
            dokarkivWebClient.post()
                .uri("dokumentInfo/${dokumentInfoId}/logiskVedlegg")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope()}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(UpdateJournalpostResponse::class.java)
                .block()
                ?: throw RuntimeException("Could not set logiske vedlegg to dokument.")
        } catch (e: Exception) {
            logger.error("Error setting logisk vedlegg for document $dokumentInfoId:", e)
        }

        logger.debug("Bulk updated logiske vedlegg for document $dokumentInfoId successfully.")
    }
}