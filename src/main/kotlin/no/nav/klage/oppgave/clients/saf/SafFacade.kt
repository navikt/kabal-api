package no.nav.klage.oppgave.clients.saf

import no.nav.klage.oppgave.clients.saf.graphql.DokumentoversiktBruker
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.clients.saf.graphql.Tema
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Component

@Component
class SafFacade(
    private val safGraphQlClient: SafGraphQlClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getDokumentoversiktBrukerAsSaksbehandler(
        fnr: String,
        tema: List<Tema>,
    ): DokumentoversiktBruker =
        safGraphQlClient.getDokumentoversiktBrukerAsSaksbehandler(
            fnr = fnr,
            tema = tema,
        )

    fun getJournalposter(
        journalpostIdSet: Set<String>,
        fnr: String?,
        saksbehandlerContext: Boolean,
        tema: List<Tema> = emptyList(),
        skipMissing: Boolean = false,
    ): List<Journalpost> {
        logger.debug(
            "getJournalposter, number of journalpostIds: ${journalpostIdSet.size}. Fnr included: ${fnr?.isNotEmpty()}. SaksbehandlerContext: $saksbehandlerContext",
        )
        return if (journalpostIdSet.size > 20 && fnr != null) {
            runWithTimingAndLogging(block = {
                val dokumentOversiktBruker =
                    safGraphQlClient.getDokumentoversiktBrukerAsSaksbehandler(
                        fnr = fnr,
                        tema = tema,
                        systemContext = !saksbehandlerContext,
                    )

                journalpostIdSet.mapNotNull { journalpostId ->
                    dokumentOversiktBruker.journalposter.find { it.journalpostId == journalpostId }
                        ?: if (skipMissing) {
                            null
                        } else {
                            throw RuntimeException(
                                "Journalpost $journalpostId not found in dokumentOversiktBruker",
                            )
                        }
                }
            }, method = "dokumentoversiktWithPaging")
        } else {
            runWithTimingAndLogging(block = {
                safGraphQlClient.getJournalposts(
                    journalpostIdSet = journalpostIdSet,
                    systemContext = !saksbehandlerContext,
                    skipMissing = skipMissing,
                )
            }, method = "getJournalposts")
        }
    }

    fun getJournalpostAsSystembruker(journalpostId: String): Journalpost =
        runWithTimingAndLogging(block = {
            safGraphQlClient.getJournalpostAsSystembruker(journalpostId = journalpostId)
        }, method = this::getJournalpostAsSystembruker.name)

    fun getJournalpostAsSaksbehandler(journalpostId: String): Journalpost =
        runWithTimingAndLogging(block = {
            safGraphQlClient.getJournalpostAsSaksbehandler(journalpostId = journalpostId)
        }, method = this::getJournalpostAsSaksbehandler.name)

    fun <T> runWithTimingAndLogging(
        block: () -> T,
        method: String,
    ): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf using $method: ${end - start} millis")
        }
    }
}
