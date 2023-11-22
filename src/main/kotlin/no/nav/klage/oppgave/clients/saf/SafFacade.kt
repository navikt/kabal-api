package no.nav.klage.oppgave.clients.saf

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

    fun getJournalposter(
        journalpostIdList: List<String>,
        fnr: String,
        tema: List<Tema> = emptyList(),
        pageSize: Int = 50000,
        previousPageRef: String? = null,
    ): List<Journalpost> {
        return if (journalpostIdList.size > 10) {
            val dokumentOversiktBruker = safGraphQlClient.getDokumentoversiktBruker(
                fnr = fnr,
                tema = tema,
                pageSize = pageSize,
                previousPageRef = previousPageRef
            )

            dokumentOversiktBruker.journalposter.filter { it.journalpostId in journalpostIdList }
        } else {
            journalpostIdList.map {
                safGraphQlClient.getJournalpostAsSaksbehandler(journalpostId = it)
            }
        }
    }
}