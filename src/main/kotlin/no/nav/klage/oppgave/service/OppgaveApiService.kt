package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.OppgaveApiMappeView
import no.nav.klage.oppgave.clients.azure.DefaultAzureGateway
import no.nav.klage.oppgave.clients.oppgaveapi.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

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

    fun getOppgave(oppgaveId: Long?): OppgaveApiRecord? {
        if (oppgaveId == null) {
            return null
        }

        return try {
            oppgaveApiClient.getOppgave(oppgaveId = oppgaveId, systemContext = false)
        } catch (e: Exception) {
            logger.error("Failed to get (gosys-) oppgave", e)
            null
        }
    }

    fun assignOppgave(
        oppgaveId: Long,
        tildeltSaksbehandlerIdent: String?,
        systemContext: Boolean,
    ) {
        val currentOppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId, systemContext = systemContext)
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
                    mappeId = null,
                )
            }

        oppgaveApiClient.updateOppgave(
            oppgaveId = oppgaveId,
            updateOppgaveInput = updateOppgaveRequest,
            systemContext = systemContext
        )
    }

    fun returnOppgave(
        oppgaveId: Long,
        tildeltEnhetsnummer: String,
        mappeId: Long?,
        kommentar: String,
    ) {
        val currentOppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId, systemContext = true)
        val endretAvEnhetsnr = "9999"

        val returnOppgaveRequest = ReturnOppgaveInput(
            versjon = currentOppgave.versjon,
            endretAvEnhetsnr = endretAvEnhetsnr,
            fristFerdigstillelse = LocalDate.now(),
            mappeId = mappeId,
            tilordnetRessurs = null,
            tildeltEnhetsnr = tildeltEnhetsnummer,
            kommentar = ReturnOppgaveInput.Kommentar(
                tekst = kommentar,
                automatiskGenerert = false
            )
        )

        oppgaveApiClient.updateOppgave(
            oppgaveId = oppgaveId,
            updateOppgaveInput = returnOppgaveRequest,
            systemContext = true,
        )
    }

    fun getMapperForEnhet(
        enhetsnr: String
    ): List<OppgaveApiMappeView> {
        val output = oppgaveApiClient.getMapperForEnhet(
            enhetsnr = enhetsnr,
        )

        return output.mapper.mapNotNull { mappe ->
            if (mappe.id != null) {
                OppgaveApiMappeView(
                    id = mappe.id,
                    navn = mappe.navn
                )
            } else null
        }.sortedBy { it.navn }
    }
}