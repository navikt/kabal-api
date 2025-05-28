package no.nav.klage.oppgave.clients.saf.graphql

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
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
        private val teamLogger = getTeamLogger()
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
                    logErrorResponse(
                        response = response,
                        functionName = ::getDokumentoversiktBrukerAsSaksbehandler.name,
                        classLogger = logger,
                    )
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

    @Retryable
    fun getJournalpostsAsSaksbehandler(journalpostIdSet: Set<String>): List<Journalpost> {
        return getJournalposts(
            journalpostIdSet = journalpostIdSet,
            token = tokenUtil.getSaksbehandlerAccessTokenWithSafScope()
        )
    }

    @Retryable
    fun getJournalpostsAsSystembruker(journalpostIdSet: Set<String>): List<Journalpost> {
        return getJournalposts(
            journalpostIdSet = journalpostIdSet,
            token = tokenUtil.getAppAccessTokenWithSafScope()
        )
    }

    @Retryable
    fun getJournalpostAsSaksbehandler(
        journalpostId: String,
    ): Journalpost {
        return getJournalpostWithToken(
            journalpostId = journalpostId,
            token = tokenUtil.getSaksbehandlerAccessTokenWithSafScope()
        )
    }

    @Retryable
    fun getJournalpostAsSystembruker(
        journalpostId: String,
    ): Journalpost {
        return getJournalpostWithToken(
            journalpostId = journalpostId,
            token = tokenUtil.getAppAccessTokenWithSafScope()
        )
    }

    private fun getJournalposts(
        journalpostIdSet: Set<String>,
        token: String,
    ): List<Journalpost> {

        return Flux.fromIterable(journalpostIdSet)
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
                    logErrorResponse(
                        response = response,
                        functionName = "getJournalpostWithTokenAsMono",
                        classLogger = logger,
                    )
                }
                .bodyToMono<JournalpostResponse>()
        } catch (e: Exception) {
            logger.warn("Could not get journalpost with id $journalpostId", e)
            Mono.empty()
        }
    }

    private fun getJournalpostWithToken(
        journalpostId: String,
        token: String,
    ): Journalpost {
        return safWebClient.post()
            .uri("graphql")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer $token"
            )
            .bodyValue(hentJournalpostQuery(journalpostId))
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = "getJournalpost",
                    classLogger = logger,
                )
            }
            .bodyToMono<JournalpostResponse>()
            .block()?.data?.journalpost ?: throw RuntimeException("Got null from SAF for journalpost with id $journalpostId")
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
            logger.error("Error from SAF, see more details in team-logs")
            teamLogger.error("Error from SAF when making call with following parameters: fnr=$fnr, pagesize=$pageSize, previousPageRef=$previousPageRef. Error is ${response.errors}")
        }
    }

    private fun logErrorsFromSaf(
        response: JournalpostResponse,
    ) {
        if (response.errors != null) {
            logger.error("Error from SAF, see more details in team-logs")
            teamLogger.error("Error from SAF when making call. Response is $response, error is ${response.errors}")
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf: ${end - start} millis")
        }
    }
}