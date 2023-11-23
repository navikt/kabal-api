package no.nav.klage.oppgave.clients.saf

import no.nav.klage.oppgave.clients.saf.graphql.DokumentoversiktBruker
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.clients.saf.graphql.Tema
import no.nav.klage.oppgave.clients.saf.rest.SafRestClient
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Component

@Component
class SafFacade(
    private val safRestClient: SafRestClient,
    private val safGraphQlClient: SafGraphQlClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getDokumentoversiktBrukerAsSaksbehandler(
        fnr: String,
        tema: List<Tema>,
        pageSize: Int,
        previousPageRef: String? = null
    ): DokumentoversiktBruker {
        return safGraphQlClient.getDokumentoversiktBrukerAsSaksbehandler(
            fnr = fnr,
            tema = tema,
            pageSize = pageSize,
            previousPageRef = previousPageRef
        )
    }

    fun getJournalposter(
        journalpostIdList: List<String>,
        fnr: String?,
        saksbehandlerContext: Boolean,
        tema: List<Tema> = emptyList(),
        pageSize: Int = 50000,
        previousPageRef: String? = null,
    ): List<Journalpost> {
        runWithTimingAndLogging({
            safGraphQlClient.getJournalpostsAsSaksbehandler(journalpostIdList = journalpostIdList)
        }, "parallell extra")

        return if (saksbehandlerContext) {
            if (journalpostIdList.size > 0 && fnr != null) {
                runWithTimingAndLogging({
                    val dokumentOversiktBruker = safGraphQlClient.getDokumentoversiktBrukerAsSaksbehandler(
                        fnr = fnr,
                        tema = tema,
                        pageSize = pageSize,
                        previousPageRef = previousPageRef
                    )

                    journalpostIdList.map { journalpostId -> dokumentOversiktBruker.journalposter.find { it.journalpostId == journalpostId }!! }
                }, "dokumentoversikt")
            } else {
                safGraphQlClient.getJournalpostsAsSaksbehandler(journalpostIdList = journalpostIdList)
            }
        } else {
            safGraphQlClient.getJournalpostsAsSystembruker(journalpostIdList = journalpostIdList)
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T, method: String): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke().let { secureLogger.debug("Received response: {}", it); it }
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf using $method: ${end - start} millis")
        }
    }
}