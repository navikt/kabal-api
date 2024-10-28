package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.api.view.GosysOppgaveApiMappeView
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.api.view.SaksbehandlerView
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
class GosysOppgaveService(
    private val gosysOppgaveClient: GosysOppgaveClient,
    private val microsoftGraphService: DefaultAzureGateway,
    private val pdlFacade: PdlFacade,
    private val norg2Client: Norg2Client,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun getGosysOppgave(gosysOppgaveId: Long, fnrToValidate: String? = null): GosysOppgaveView {
        val gosysOppgaveRecord = gosysOppgaveClient.getGosysOppgave(gosysOppgaveId, systemContext = false)
        if (fnrToValidate != null) {
            if (gosysOppgaveRecord.bruker.ident != fnrToValidate) {
                throw IllegalOperation("Gosys-oppgave hører ikke til angitt person")
            }
        }
        return gosysOppgaveRecord.toGosysOppgaveView()
    }

    fun assignGosysOppgave(
        gosysOppgaveId: Long,
        tildeltSaksbehandlerIdent: String?,
        systemContext: Boolean,
    ) {
        val currentGosysOppgave = gosysOppgaveClient.getGosysOppgave(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)
        if (!currentGosysOppgave.isEditable()) {
            logger.warn("Oppgave $gosysOppgaveId kan ikke oppdateres, returnerer")
            return
        }

        val endretAvEnhetsnr = if (systemContext) "9999" else {
            microsoftGraphService.getDataOmInnloggetSaksbehandler().enhet.enhetId
        }
        val updateGosysOppgaveRequest =
            if (tildeltSaksbehandlerIdent.isNullOrBlank()) {
                FradelGosysOppgaveInput(
                    versjon = currentGosysOppgave.versjon,
                    endretAvEnhetsnr = endretAvEnhetsnr,
                    tilordnetRessurs = null,
                )
            } else {
                val tildeltSaksbehandlerInfo =
                    microsoftGraphService.getPersonligDataOmSaksbehandlerMedIdent(tildeltSaksbehandlerIdent)

                TildelGosysOppgaveInput(
                    versjon = currentGosysOppgave.versjon,
                    endretAvEnhetsnr = endretAvEnhetsnr,
                    tilordnetRessurs = tildeltSaksbehandlerIdent,
                    tildeltEnhetsnr = tildeltSaksbehandlerInfo.enhet.enhetId,
                    mappeId = null,
                )
            }

        gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = gosysOppgaveId,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = systemContext
        )
    }

    fun updateGosysOppgave(
        gosysOppgaveId: Long,
        tildeltEnhetsnummer: String,
        mappeId: Long?,
        kommentar: String,
    ) {
        val currentGosysOppgave = gosysOppgaveClient.getGosysOppgave(gosysOppgaveId = gosysOppgaveId, systemContext = true)

        val endretAvEnhetsnr = "9999"

        val updateGosysOppgaveRequest = UpdateGosysOppgaveInput(
            versjon = currentGosysOppgave.versjon,
            endretAvEnhetsnr = endretAvEnhetsnr,
            fristFerdigstillelse = LocalDate.now(),
            mappeId = mappeId,
            tilordnetRessurs = null,
            tildeltEnhetsnr = tildeltEnhetsnummer,
            kommentar = UpdateGosysOppgaveInput.Kommentar(
                tekst = kommentar,
                automatiskGenerert = false
            )
        )

        gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = gosysOppgaveId,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = true,
        )
    }

    fun getMapperForEnhet(
        enhetsnr: String
    ): List<GosysOppgaveApiMappeView> {
        val output = gosysOppgaveClient.getMapperForEnhet(
            enhetsnr = enhetsnr,
        )

        return output.mapper.mapNotNull { mappe ->
            if (mappe.id != null) {
                GosysOppgaveApiMappeView(
                    id = mappe.id,
                    navn = mappe.navn
                )
            } else null
        }.sortedBy { it.navn }
    }

    fun getMappe(
        id: Long
    ): GosysOppgaveApiMappeView {
        val mappeResponse = gosysOppgaveClient.getMappe(id = id)

        if (mappeResponse.id == null) {
            throw OppgaveClientException("Mappe did not contain id")
        }

        return GosysOppgaveApiMappeView(
            id = mappeResponse.id,
            navn = mappeResponse.navn
        )
    }

    fun getGosysOppgaveList(fnr: String, tema: Tema?): List<GosysOppgaveView> {
        val aktoerId = pdlFacade.getAktorIdFromIdent(ident = fnr)

        val gosysOppgaveList = gosysOppgaveClient.fetchGosysOppgaveForAktoerIdAndTema(
            aktoerId = aktoerId,
            tema = tema,
        )

        return gosysOppgaveList.map { it.toGosysOppgaveView() }
    }

    fun GosysOppgaveRecord.toGosysOppgaveView(): GosysOppgaveView {
        val tema = Tema.fromNavn(tema)
        return GosysOppgaveView(
            id = id,
            tildeltEnhetsnr = tildeltEnhetsnr,
            endretAvEnhetsnr = endretAvEnhetsnr,
            endretAv = endretAv.navIdentToSaksbehandlerView(),
            endretTidspunkt = endretTidspunkt,
            opprettetAv = opprettetAv.navIdentToSaksbehandlerView(),
            opprettetTidspunkt = opprettetTidspunkt,
            beskrivelse = beskrivelse,
            temaId = tema.id,
            gjelder = getGjelder(behandlingstype = behandlingstype, tema = tema),
            oppgavetype = getOppgavetype(oppgavetype = oppgavetype, tema = tema),
            fristFerdigstillelse = fristFerdigstillelse,
            ferdigstiltTidspunkt = ferdigstiltTidspunkt,
            status = GosysOppgaveView.Status.valueOf(status.name),
            mappe = if (mappeId != null) {
                getMappe(id = mappeId)
            } else null,
            editable = isEditable(),
            opprettetAvEnhet = opprettetAvEnhetsnr?.let {
                EnhetView(
                    enhetsnr = it,
                    navn = norg2Client.fetchEnhet(enhetNr = it).navn,
                )
            },
            alreadyUsedBy = null,
        )
    }

    private fun String?.navIdentToSaksbehandlerView(): SaksbehandlerView? {
        return if (this != null) {
            SaksbehandlerView(
                navIdent = this,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(navIdent = this),
            )
        } else null
    }

    private fun getGjelder(behandlingstype: String?, tema: Tema): String? {
        return getGjelderKodeverkForTema(tema = tema).firstOrNull { it.behandlingstype == behandlingstype }?.behandlingstypeTerm
    }

    private fun getOppgavetype(oppgavetype: String?, tema: Tema): String? {
        return getOppgavetypeKodeverkForTema(tema = tema).firstOrNull { it.oppgavetype == oppgavetype }?.term
    }

    private fun getGjelderKodeverkForTema(tema: Tema): List<Gjelder> {
        return gosysOppgaveClient.getGjelderKodeverkForTema(tema = tema)
    }

    private fun getOppgavetypeKodeverkForTema(tema: Tema): List<OppgavetypeResponse> {
        return gosysOppgaveClient.getOppgavetypeKodeverkForTema(tema = tema)
    }
}