package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.api.view.GosysOppgaveMappeView
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.clients.azure.DefaultAzureGateway
import no.nav.klage.oppgave.clients.gosysoppgave.*
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.kafka.Employee
import no.nav.klage.oppgave.domain.kafka.GosysoppgaveEvent
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.exceptions.GosysOppgaveNotEditableException
import no.nav.klage.oppgave.exceptions.IllegalOperation
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class GosysOppgaveService(
    private val gosysOppgaveClient: GosysOppgaveClient,
    private val microsoftGraphService: DefaultAzureGateway,
    private val pdlFacade: PdlFacade,
    private val norg2Client: Norg2Client,
    private val saksbehandlerService: SaksbehandlerService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()

        const val ENDRET_AV_ENHETSNR_SYSTEM = "9999"
    }

    fun getGosysOppgave(gosysOppgaveId: Long, fnrToValidate: String? = null): GosysOppgaveView {
        val gosysOppgaveRecord = gosysOppgaveClient.getGosysOppgave(gosysOppgaveId, systemContext = false)
        if (fnrToValidate != null) {
            if (gosysOppgaveRecord.bruker.ident != fnrToValidate) {
                throw IllegalOperation("Gosys-oppgave hører ikke til angitt person")
            }
        }
        return gosysOppgaveRecord.toGosysOppgaveView(systemContext = false)
    }

    fun assignGosysOppgave(
        gosysOppgaveId: Long,
        tildeltSaksbehandlerIdent: String?,
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        val systemContext = utfoerendeSaksbehandlerIdent == systembrukerIdent

        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgave(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
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

        val updatedGosysOppgave = gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = gosysOppgaveId,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = systemContext
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                GosysoppgaveEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    gosysOppgave = updatedGosysOppgave.toGosysOppgaveView(systemContext = true),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.GOSYSOPPGAVE,
        )
    }

    fun updateGosysOppgave(
        behandling: Behandling,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        val gosysOppgaveId = behandling.gosysOppgaveId!!

        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgave(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val updateGosysOppgaveRequest = UpdateGosysOppgaveInput(
            versjon = currentGosysOppgave.versjon,
            endretAvEnhetsnr = ENDRET_AV_ENHETSNR_SYSTEM,
            fristFerdigstillelse = LocalDate.now(),
            mappeId = behandling.gosysOppgaveUpdate!!.oppgaveUpdateMappeId,
            tilordnetRessurs = null,
            tildeltEnhetsnr = behandling.gosysOppgaveUpdate!!.oppgaveUpdateTildeltEnhetsnummer,
            kommentar = Kommentar(
                tekst = behandling.gosysOppgaveUpdate!!.oppgaveUpdateKommentar,
                automatiskGenerert = false
            )
        )

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = systemContext
        )
    }


    fun addKommentar(
        behandling: Behandling,
        kommentar: String,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        logger.debug("Adding kommentar to Gosys-oppgave ${behandling.gosysOppgaveId}")
        val currentGosysOppgave = gosysOppgaveClient.getGosysOppgave(
            gosysOppgaveId = behandling.gosysOppgaveId!!,
            systemContext = systemContext
        )

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val updateGosysOppgaveRequest = AddKommentarToGosysOppgaveInput(
            versjon = currentGosysOppgave.versjon,
            endretAvEnhetsnr = ENDRET_AV_ENHETSNR_SYSTEM,
            kommentar = Kommentar(
                tekst = kommentar,
                automatiskGenerert = false
            )
        )

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = true
        )
    }

    fun avsluttGosysOppgave(
        behandling: Behandling,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        logger.debug("Avslutter Gosys-oppgave ${behandling.gosysOppgaveId}")
        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgave(gosysOppgaveId = behandling.gosysOppgaveId!!, systemContext = true)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val avsluttGosysOppgaveInput = AvsluttGosysOppgaveInput(
            versjon = currentGosysOppgave.versjon,
            endretAvEnhetsnr = ENDRET_AV_ENHETSNR_SYSTEM,
            status = Status.FERDIGSTILT,
            kommentar = Kommentar(
                tekst = "Klageinstansen har ferdigstilt behandlingen i Kabal med utfall: ${behandling.utfall!!.navn}.",
                automatiskGenerert = true
            )
        )

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = avsluttGosysOppgaveInput,
            systemContext = true
        )
    }

    private fun updateOppgaveAndPublishEvent(
        behandling: Behandling,
        updateGosysOppgaveRequest: UpdateOppgaveRequest,
        systemContext: Boolean,
    ) {
        val updatedGosysOppgave = gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = behandling.gosysOppgaveId!!,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = systemContext,
        )

        val saksbehandlerident = behandling.tildeling?.saksbehandlerident ?: systembrukerIdent

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                GosysoppgaveEvent(
                    actor = Employee(
                        navIdent = saksbehandlerident,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(saksbehandlerident),
                    ),
                    timestamp = LocalDateTime.now(),
                    gosysOppgave = updatedGosysOppgave.toGosysOppgaveView(systemContext = systemContext),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.GOSYSOPPGAVE,
        )
    }

    fun getMapperForEnhet(
        enhetsnr: String
    ): List<GosysOppgaveMappeView> {
        val output = gosysOppgaveClient.getMapperForEnhet(
            enhetsnr = enhetsnr,
        )

        return output.mapper.mapNotNull { mappe ->
            if (mappe.id != null) {
                GosysOppgaveMappeView(
                    id = mappe.id,
                    navn = mappe.navn
                )
            } else null
        }.sortedBy { it.navn }
    }

    fun getMappe(
        id: Long,
        systemContext: Boolean
    ): GosysOppgaveMappeView {
        val mappeResponse = gosysOppgaveClient.getMappe(id = id, systemContext = systemContext)

        if (mappeResponse.id == null) {
            throw OppgaveClientException("Mappe did not contain id")
        }

        return GosysOppgaveMappeView(
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

        return gosysOppgaveList.map { it.toGosysOppgaveView(systemContext = false) }
    }

    fun GosysOppgaveRecord.toGosysOppgaveView(systemContext: Boolean): GosysOppgaveView {
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
            gjelder = getGjelder(behandlingstype = behandlingstype, tema = tema, systemContext = systemContext),
            oppgavetype = getOppgavetype(oppgavetype = oppgavetype, tema = tema, systemContext = systemContext),
            fristFerdigstillelse = fristFerdigstillelse,
            ferdigstiltTidspunkt = ferdigstiltTidspunkt,
            status = GosysOppgaveView.Status.valueOf(status.name),
            mappe = if (mappeId != null) {
                getMappe(id = mappeId, systemContext = systemContext)
            } else null,
            editable = isEditable(),
            opprettetAvEnhet = if (opprettetAvEnhetsnr != null && opprettetAvEnhetsnr.trim() != "0") {
                EnhetView(
                    enhetsnr = opprettetAvEnhetsnr,
                    navn = norg2Client.fetchEnhet(enhetNr = opprettetAvEnhetsnr).navn,
                )
            } else null,
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

    private fun getGjelder(behandlingstype: String?, tema: Tema, systemContext: Boolean): String? {
        return getGjelderKodeverkForTema(
            tema = tema,
            systemContext = systemContext
        ).firstOrNull { it.behandlingstype == behandlingstype }?.behandlingstypeTerm
    }

    private fun getOppgavetype(oppgavetype: String?, tema: Tema, systemContext: Boolean): String? {
        return getOppgavetypeKodeverkForTema(
            tema = tema,
            systemContext = systemContext
        ).firstOrNull { it.oppgavetype == oppgavetype }?.term
    }

    private fun getGjelderKodeverkForTema(tema: Tema, systemContext: Boolean): List<Gjelder> {
        return gosysOppgaveClient.getGjelderKodeverkForTema(tema = tema, systemContext = systemContext)
    }

    private fun getOppgavetypeKodeverkForTema(tema: Tema, systemContext: Boolean): List<OppgavetypeResponse> {
        return gosysOppgaveClient.getOppgavetypeKodeverkForTema(tema = tema, systemContext = systemContext)
    }

    private fun publishInternalEvent(data: String, behandlingId: UUID, type: InternalEventType) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = behandlingId.toString(),
                type = type,
                data = data,
            )
        )
    }

    private fun shouldAttemptGosysOppgaveUpdate(
        currentGosysOppgave: GosysOppgaveRecord,
        throwExceptionIfFerdigstilt: Boolean
    ): Boolean {
        val gosysOppgaveId = currentGosysOppgave.id
        return if (!currentGosysOppgave.isEditable()) {
            if (throwExceptionIfFerdigstilt) {
                throw GosysOppgaveNotEditableException("Gosys-oppgave $gosysOppgaveId kan ikke oppdateres fordi status er ${currentGosysOppgave.status}")
            } else {
                logger.warn("Gosys-oppgave $gosysOppgaveId kan ikke oppdateres, returnerer")
                return false
            }
        } else true
    }
}