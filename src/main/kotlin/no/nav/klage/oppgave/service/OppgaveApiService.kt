package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.azure.DefaultAzureGateway
import no.nav.klage.oppgave.clients.oppgaveapi.OppgaveApiClient
import no.nav.klage.oppgave.clients.oppgaveapi.UpdateOppgaveInput
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class OppgaveApiService(
    private val oppgaveApiClient: OppgaveApiClient,
    private val tokenUtil: TokenUtil,
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

    fun updateOppgaveWithTildeling(
        oppgaveId: Long,
        tildeltSaksbehandlerIdent: String?
    ) {
        val currentUserInfo = microsoftGraphService.getDataOmInnloggetSaksbehandler()
        val currentOppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId)

        val (tilordnetRessurs, tildeltEnhetsnr, newBeskrivelse) = if (tildeltSaksbehandlerIdent != null) {
            val tildeltSaksbehandlerInfo =
                microsoftGraphService.getPersonligDataOmSaksbehandlerMedIdent(tildeltSaksbehandlerIdent)
            Triple(
                tildeltSaksbehandlerIdent,
                tildeltSaksbehandlerInfo.enhet.enhetId,
                "Tildelte oppgaven til $tildeltSaksbehandlerIdent."
            )
        } else {
            if (currentOppgave.tilordnetRessurs == null) {
                return
            }
            //Oppgaven er flyttet  fra saksbehandler Z994121 til <ingen>
            Triple(
                null,
                null,
                "Oppgaven er flyttet  fra saksbehandler ${currentOppgave.tilordnetRessurs} til <ingen>"
            )
        }

        val newComment = "Overførte oppgaven fra Kabin til Kabal."

        oppgaveApiClient.updateOppgave(
            oppgaveId = oppgaveId,
            updateOppgaveInput = UpdateOppgaveInput(
                versjon = currentOppgave.versjon,
                fristFerdigstillelse = null,
                mappeId = null,
                endretAvEnhetsnr = currentUserInfo.enhet.enhetId,
                tilordnetRessurs = tilordnetRessurs,
                tildeltEnhetsnr = tildeltEnhetsnr,
                beskrivelse = getNewBeskrivelse(
                    newBeskrivelsePart = newBeskrivelsePart,
                    existingBeskrivelse = currentOppgave.beskrivelse,
                    currentUserInfo = currentUserInfo
                ),
                kommentar = UpdateOppgaveInput.Kommentar(
                    tekst = newComment,
                    automatiskGenerert = true
                ),
                tema = null,
                prioritet = null,
                orgnr = null,
                status = null,
                behandlingstema = null,
                behandlingstype = null,
                aktivDato = null,
                oppgavetype = null,
                journalpostId = null,
                saksreferanse = null,
                behandlesAvApplikasjon = null,
                personident = null,
            )
        )
    }

    private fun getNewBeskrivelse(
        newBeskrivelsePart: String,
        existingBeskrivelse: String?,
        currentUserInfo: SaksbehandlerPersonligInfo,
    ): String {
        val formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

        val nameOfCurrentUser = currentUserInfo.sammensattNavn
        val currentUserEnhet = currentUserInfo.enhet.enhetId
        val header = "--- $formattedDate $nameOfCurrentUser (${currentUserInfo.navIdent}, $currentUserEnhet) ---"
        return "$header\n$newBeskrivelsePart\n\n$existingBeskrivelse\n".trimIndent()
    }

}