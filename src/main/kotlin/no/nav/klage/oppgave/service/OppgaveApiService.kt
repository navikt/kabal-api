package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.oppgaveapi.OppgaveApiClient
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class OppgaveApiService(
    private val oppgaveApiClient: OppgaveApiClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun getOppgaveEntryView(oppgaveId: Long): String? {
        val oppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId)
        return oppgave.beskrivelse
    }

}