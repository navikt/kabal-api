package no.nav.klage.oppgave.clients.dokarkiv

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

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

    fun addLogiskVedleggOnBehalfOf(
        dokumentInfoId: String,
        title: String,
    ): AddLogiskVedleggResponse {
        try {
            val response = dokarkivWebClient.post()
                .uri("/dokumentInfo/${dokumentInfoId}/logiskVedlegg/")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope()}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    LogiskVedleggPayload(
                        tittel = title
                    )
                )
                .retrieve()
                .bodyToMono(AddLogiskVedleggResponse::class.java)
                .block()
                ?: throw RuntimeException("Could not add logisk vedlegg to documentInfoId $dokumentInfoId.")
            logger.debug("Added logisk vedlegg to document $dokumentInfoId successfully.")
            return response
        } catch (e: Exception) {
            logger.error("Error adding logisk vedlegg to document $dokumentInfoId:", e)
            throw e
        }
    }

    fun updateLogiskVedleggOnBehalfOf(
        dokumentInfoId: String,
        logiskVedleggId: String,
        title: String,
    ) {
        try {
            dokarkivWebClient.post()
                .uri("/dokumentInfo/${dokumentInfoId}/logiskVedlegg/${logiskVedleggId}")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope()}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    LogiskVedleggPayload(
                        tittel = title,
                    )
                )
                .retrieve()
                .bodyToMono<Void>()
                .block()
                ?: throw RuntimeException("Could not update logisk vedlegg $logiskVedleggId for documentInfoId $dokumentInfoId.")
            logger.debug("Updated logisk vedlegg $logiskVedleggId for document $dokumentInfoId successfully.")
        } catch (e: Exception) {
            logger.error("Error updating logisk vedlegg $dokumentInfoId for document $dokumentInfoId:", e)
        }
    }

    fun deleteLogiskVedleggOnBehalfOf(
        dokumentInfoId: String,
        logiskVedleggId: String
    ) {
        try {
            dokarkivWebClient.delete()
                .uri("/dokumentInfo/${dokumentInfoId}/logiskVedlegg/${logiskVedleggId}")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope()}"
                )
                .retrieve()
                .bodyToMono<Void>()
                .block()
                ?: throw RuntimeException("Could not delete logisk vedlegg $logiskVedleggId for documentInfoId $dokumentInfoId.")
            logger.debug("Deleted logisk vedlegg $logiskVedleggId for document $dokumentInfoId successfully.")
        } catch (e: Exception) {
            logger.error("Error deleting logisk vedlegg $dokumentInfoId for document $dokumentInfoId:", e)
        }
    }
}