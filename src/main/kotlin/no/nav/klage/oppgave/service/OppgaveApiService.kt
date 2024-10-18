package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.api.view.OppgaveApiMappeView
import no.nav.klage.oppgave.clients.azure.DefaultAzureGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.oppgaveapi.*
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.exceptions.IllegalOperation
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class OppgaveApiService(
    private val oppgaveApiClient: OppgaveApiClient,
    private val microsoftGraphService: DefaultAzureGateway,
    private val pdlFacade: PdlFacade,
    private val norg2Client: Norg2Client,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun getOppgave(oppgaveId: Long, fnrToValidate: String? = null): GosysOppgaveView {
        val oppgave = oppgaveApiClient.getOppgave(oppgaveId, systemContext = false)
        if (fnrToValidate != null) {
            if (oppgave.bruker.ident != fnrToValidate) {
                throw IllegalOperation("Gosys-oppgave hører ikke til angitt person")
            }
        }
        return oppgave.toGosysOppgaveView()
    }

    fun assignOppgave(
        oppgaveId: Long,
        tildeltSaksbehandlerIdent: String?,
        systemContext: Boolean,
    ) {
        val currentOppgave = oppgaveApiClient.getOppgave(oppgaveId = oppgaveId, systemContext = systemContext)
        if (!currentOppgave.isEditable()) {
            logger.warn("Oppgave $oppgaveId kan ikke oppdateres, returnerer")
            return
        }

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

    fun getOppgaveList(fnr: String, tema: Tema?): List<GosysOppgaveView> {
        val aktoerId = pdlFacade.getAktorIdFromIdent(ident = fnr)

        val oppgaveList = oppgaveApiClient.fetchOppgaveForAktoerIdAndTema(
            aktoerId = aktoerId,
            tema = tema,
        )

        return oppgaveList.map { it.toGosysOppgaveView() }
    }

    fun OppgaveApiRecord.toGosysOppgaveView(): GosysOppgaveView {
        val tema = Tema.fromNavn(tema)
        return GosysOppgaveView(
            id = id,
            tildeltEnhetsnr = tildeltEnhetsnr,
            endretAvEnhetsnr = endretAvEnhetsnr,
            endretAv = endretAv,
            endretTidspunkt = endretTidspunkt,
            opprettetAv = opprettetAv,
            opprettetTidspunkt = opprettetTidspunkt,
            beskrivelse = beskrivelse,
            temaId = tema.id,
            gjelder = getGjelder(behandlingstype = behandlingstype, tema = tema),
            oppgavetype = getOppgavetype(oppgavetype = oppgavetype, tema = tema),
            fristFerdigstillelse = fristFerdigstillelse,
            ferdigstiltTidspunkt = ferdigstiltTidspunkt,
            status = status,
            editable = isEditable(),
            opprettetAvEnhet = opprettetAvEnhetsnr?.let {
                EnhetView(
                    enhetsnr = it,
                    navn = norg2Client.fetchEnhet(enhetNr = it).navn,
                )
            },
            alreadyUsed = false,
        )
    }

    private fun getGjelder(behandlingstype: String?, tema: Tema): String? {
        return getGjelderKodeverkForTema(tema = tema).firstOrNull { it.behandlingstype == behandlingstype }?.behandlingstypeTerm
    }

    private fun getOppgavetype(oppgavetype: String?, tema: Tema): String? {
        return getOppgavetypeKodeverkForTema(tema = tema).firstOrNull { it.oppgavetype == oppgavetype }?.term
    }

    private fun getGjelderKodeverkForTema(tema: Tema): List<Gjelder> {
        return oppgaveApiClient.getGjelderKodeverkForTema(tema = tema)
    }

    private fun getOppgavetypeKodeverkForTema(tema: Tema): List<OppgavetypeResponse> {
        return oppgaveApiClient.getOppgavetypeKodeverkForTema(tema = tema)
    }
}