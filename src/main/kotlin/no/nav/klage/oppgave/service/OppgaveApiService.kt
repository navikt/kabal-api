package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.azure.DefaultAzureGateway
import no.nav.klage.oppgave.clients.oppgaveapi.FradelOppgaveInput
import no.nav.klage.oppgave.clients.oppgaveapi.OppgaveApiClient
import no.nav.klage.oppgave.clients.oppgaveapi.TildelOppgaveInput
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class OppgaveApiService(
    private val oppgaveApiClient: OppgaveApiClient,
    private val microsoftGraphService: DefaultAzureGateway,
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

    fun assignOppgave(
        oppgaveId: Long,
        tildeltSaksbehandlerIdent: String?,
        systemContext: Boolean,
    ) {
        val currentOppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId)
        val endretAvEnhetsnr = if (systemContext) "9999" else {
            microsoftGraphService.getDataOmInnloggetSaksbehandler().enhet.enhetId
        }
        val updateOppgaveRequest =
            if (tildeltSaksbehandlerIdent.isNullOrBlank()) {
                FradelOppgaveInput(
                    versjon = currentOppgave.versjon,
                    endretAvEnhetsnr = endretAvEnhetsnr,
                    tilordnetRessurs = null,
                )
            } else {
                val tildeltSaksbehandlerInfo =
                    microsoftGraphService.getPersonligDataOmSaksbehandlerMedIdent(tildeltSaksbehandlerIdent)

                TildelOppgaveInput(
                    versjon = currentOppgave.versjon,
                    endretAvEnhetsnr = endretAvEnhetsnr,
                    tilordnetRessurs = tildeltSaksbehandlerIdent,
                    tildeltEnhetsnr = tildeltSaksbehandlerInfo.enhet.enhetId,
                )
            }

        oppgaveApiClient.updateOppgave(
            oppgaveId = oppgaveId,
            updateOppgaveInput = updateOppgaveRequest,
            systemContext = systemContext
        )
    }
}