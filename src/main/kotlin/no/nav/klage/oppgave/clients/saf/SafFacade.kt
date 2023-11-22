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
        tema: List<Tema>,
        pageSize: Int,
        previousPageRef: String?,
    ): List<Journalpost> {
        val dokumentOversiktBruker = safGraphQlClient.getDokumentoversiktBruker(
            fnr = fnr,
            tema = tema,
            pageSize = pageSize,
            previousPageRef = previousPageRef
        )

        return dokumentOversiktBruker.journalposter.filter { it.journalpostId in journalpostIdList }
    }
}