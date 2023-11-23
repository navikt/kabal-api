package no.nav.klage.oppgave.clients.saf.graphql

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDateTime

@Component
class SafGraphQlClient(
    private val safWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Retryable
    fun getDokumentoversiktBrukerAsSaksbehandler(
        fnr: String,
        tema: List<Tema>,
        pageSize: Int,
        previousPageRef: String? = null
    ): DokumentoversiktBruker {
        val start = System.currentTimeMillis()
        return runWithTimingAndLogging {
            safWebClient.post()
                .uri("graphql")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithSafScope()}"
                )
                .bodyValue(hentDokumentoversiktBrukerQuery(fnr, tema, pageSize, previousPageRef))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::getDokumentoversiktBrukerAsSaksbehandler.name, secureLogger)
                }
                .bodyToMono<DokumentoversiktBrukerResponse>()
                .block()
                ?.let { logErrorsFromSaf(it, fnr, pageSize, previousPageRef); it }
                ?.let { failOnErrors(it); it }
                ?.data!!.dokumentoversiktBruker.also {
                    logger.debug(
                        "DokumentoversiktBruker: antall: {}, ms: {}, dato/tid: {}",
                        it.sideInfo.totaltAntall,
                        System.currentTimeMillis() - start,
                        LocalDateTime.now()
                    )
                }
        }
    }

    fun getJournalpostsAsSaksbehandler(journalpostIdList: List<String>): List<Journalpost> {
        return getJournalposts(
            journalpostIdList = journalpostIdList,
            token = tokenUtil.getSaksbehandlerAccessTokenWithSafScope()
        )
    }

    fun getJournalpostsAsSystembruker(journalpostIdList: List<String>): List<Journalpost> {
        return getJournalposts(
            journalpostIdList = journalpostIdList,
            token = tokenUtil.getAppAccessTokenWithSafScope()
        )
    }

    fun getJournalposts(
        journalpostIdList: List<String>,
        token: String,
    ): List<Journalpost> {
        return Flux.fromIterable(journalpostIdList)
            .parallel()
            .runOn(Schedulers.boundedElastic())
            .flatMap { journalpostId ->
                getJournalpostWithTokenAsMono(
                    journalpostId = journalpostId,
                    token = token
                )
            }
            .ordered { _: JournalpostResponse, _: JournalpostResponse -> 1 }.toIterable()
            .mapNotNull {
                if (it == null) throw RuntimeException("No response from SAF")
                logErrorsFromSaf(it)
                failOnErrors(it)
                it.data!!.journalpost
            }
    }

    @Retryable
    private fun getJournalpostWithTokenAsMono(
        journalpostId: String,
        token: String
    ): Mono<JournalpostResponse> {
        return try {
            safWebClient.post()
                .uri("graphql")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer $token"
                )
                .bodyValue(hentJournalpostQuery(journalpostId))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, "getJournalpostWithTokenAsMono", secureLogger)
                }
                .bodyToMono<JournalpostResponse>()
        } catch (e: Exception) {
            logger.warn("Could not get journalpost with id $journalpostId", e)
            Mono.empty()
        }
    }

    private fun failOnErrors(response: JournalpostResponse) {
        if (response.data?.journalpost == null || (response.errors != null && response.errors.map { it.extensions.classification }
                .contains("ValidationError"))) {
            throw RuntimeException("getJournalpost failed")
        }
    }

    private fun failOnErrors(response: DokumentoversiktBrukerResponse) {
        if (response.data == null || response.errors != null) {
            throw RuntimeException("getDokumentoversiktBruker failed")
        }
    }

    private fun logErrorsFromSaf(
        response: DokumentoversiktBrukerResponse,
        fnr: String,
        pageSize: Int,
        previousPageRef: String?
    ) {
        if (response.errors != null) {
            logger.error("Error from SAF, see securelogs")
            secureLogger.error("Error from SAF when making call with following parameters: fnr=$fnr, pagesize=$pageSize, previousPageRef=$previousPageRef. Error is ${response.errors}")
        }
    }

    private fun logErrorsFromSaf(
        response: JournalpostResponse,
    ) {
        if (response.errors != null) {
            logger.error("Error from SAF, see securelogs")
            secureLogger.error("Error from SAF when making call. Error is ${response.errors}")
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke().let { secureLogger.debug("Received response: {}", it); it }
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf: ${end - start} millis")
        }
    }
}