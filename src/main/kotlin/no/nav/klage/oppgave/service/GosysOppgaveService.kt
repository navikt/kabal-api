package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Tema
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.api.view.GosysOppgaveMappeView
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.clients.gosysoppgave.*
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.exceptions.GosysOppgaveClientException
import no.nav.klage.oppgave.exceptions.GosysOppgaveNotEditableException
import no.nav.klage.oppgave.exceptions.IllegalOperation
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class GosysOppgaveService(
    private val gosysOppgaveClient: GosysOppgaveClient,
    private val personService: PersonService,
    private val norg2Client: Norg2Client,
    private val saksbehandlerService: SaksbehandlerService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val klageLookupGateway: KlageLookupGateway,
    private val tokenUtil: TokenUtil,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    fun getGosysOppgave(gosysOppgaveId: Long, fnrToValidate: String? = null): GosysOppgaveView {
        val gosysOppgaveRecord = gosysOppgaveClient.getGosysOppgaveV2(gosysOppgaveId, systemContext = false)
        if (fnrToValidate != null) {
            if (gosysOppgaveRecord.bruker?.ident != fnrToValidate) {
                throw IllegalOperation("Gosys-oppgave hører ikke til angitt person")
            }
        }
        return gosysOppgaveRecord.toGosysOppgaveView()
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
            gosysOppgaveClient.getGosysOppgaveV2(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val representerer = getRepresenterer(systemContext = systemContext)

        val fradelingRequest = FordelingFradelingRequest(
            medarbeider = null,
        )

        val updateGosysOppgaveRequest =
            if (representerer == null) {
                if (tildeltSaksbehandlerIdent.isNullOrBlank()) {
                    FradelGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMeta(
                            versjon = currentGosysOppgave.versjon,
                        ),
                        fordeling = fradelingRequest,
                    )
                } else {
                    val tildeltSaksbehandlerInfo =
                        klageLookupGateway.getUserInfoForGivenNavIdent(
                            navIdent = tildeltSaksbehandlerIdent,
                        )
                    TildelGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMeta(
                            versjon = currentGosysOppgave.versjon,
                        ),
                        fordeling = FordelingTildelingRequest(
                            enhet = EnhetDto(
                                nr = tildeltSaksbehandlerInfo.enhet.enhetId,
                            ),
                            mappe = null,
                            medarbeider = MedarbeiderRequestDto(
                                navident = tildeltSaksbehandlerIdent,
                            ),
                        ),
                    )
                }
            } else {
                if (tildeltSaksbehandlerIdent.isNullOrBlank()) {
                    FradelGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithRepresenterer(
                            versjon = currentGosysOppgave.versjon,
                            representerer = representerer,
                        ),
                        fordeling = fradelingRequest,
                    )
                } else {
                    val tildeltSaksbehandlerInfo =
                        klageLookupGateway.getUserInfoForGivenNavIdent(
                            navIdent = tildeltSaksbehandlerIdent,
                        )
                    TildelGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithRepresenterer(
                            versjon = currentGosysOppgave.versjon,
                            representerer = representerer,
                        ),
                        fordeling = FordelingTildelingRequest(
                            enhet = EnhetDto(
                                nr = tildeltSaksbehandlerInfo.enhet.enhetId,
                            ),
                            mappe = null,
                            medarbeider = MedarbeiderRequestDto(
                                navident = tildeltSaksbehandlerIdent,
                            )
                        ),
                    )
                }
            }

        val updatedGosysOppgave = gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = gosysOppgaveId,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = systemContext
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                GosysoppgaveEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    gosysOppgave = updatedGosysOppgave.toGosysOppgaveView(),
                    traceparent = currentTraceparent(),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.GOSYSOPPGAVE,
        )
    }

    fun updateInternalFristInGosysOppgave(
        behandling: Behandling,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        val gosysOppgaveId = behandling.gosysOppgaveId!!

        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgaveV2(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        if (behandling.frist == null) {
            logger.warn("Behandling has no frist. Cannot update frist in Gosys-oppgave.")
            return
        }

        val representerer = getRepresenterer(systemContext = systemContext)
        val kommentar = "Frist satt på bakgrunn av intern frist i Kabal."

        val updateGosysOppgaveRequest = if (representerer == null) {
            UpdateFristInGosysOppgaveRequestWithoutRepresenterer(
                meta = PatchMetaWithKommentar(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                ),
                fristDato = behandling.frist!!
            )
        } else {
            UpdateFristInGosysOppgaveRequestWithRepresenterer(
                meta = PatchMetaWithKommentarAndRepresenterer(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                    representerer = representerer
                ),
                fristDato = behandling.frist!!
            )
        }

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = systemContext
        )
    }

    fun updateVarsletFristInGosysOppgave(
        behandling: Behandling,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        if (behandling !is BehandlingWithVarsletBehandlingstid) {
            logger.error("Behandling is not BehandlingWithVarsletBehandlingstid. Cannot update frist in Gosys-oppgave.")
            return
        }

        val gosysOppgaveId = behandling.gosysOppgaveId!!

        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgaveV2(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val representerer = getRepresenterer(systemContext = systemContext)
        val kommentar = "Frist satt på bakgrunn av varslet behandlingstid."

        val updateGosysOppgaveRequest = if (representerer == null) {
            UpdateFristInGosysOppgaveRequestWithoutRepresenterer(
                meta = PatchMetaWithKommentar(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                ),
                fristDato = behandling.varsletBehandlingstid!!.varsletFrist!!
            )
        } else {
            UpdateFristInGosysOppgaveRequestWithRepresenterer(
                meta = PatchMetaWithKommentarAndRepresenterer(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                    representerer = representerer
                ),
                fristDato = behandling.varsletBehandlingstid!!.varsletFrist!!
            )
        }

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = systemContext
        )
    }

    fun updateGosysOppgaveOnCompletedBehandling(
        behandling: Behandling,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ) {
        val gosysOppgaveId = behandling.gosysOppgaveId!!

        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgaveV2(gosysOppgaveId = gosysOppgaveId, systemContext = systemContext)

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return
        }

        val representerer = getRepresenterer(systemContext = systemContext)

        val kommentar = behandling.gosysOppgaveUpdate!!.oppgaveUpdateKommentar

        val fordeling = FordelingTildelingRequest(
            enhet = EnhetDto(
                nr = behandling.gosysOppgaveUpdate!!.oppgaveUpdateTildeltEnhetsnummer,
            ),
            mappe = behandling.gosysOppgaveUpdate!!.oppgaveUpdateMappeId?.let {
                MappeRequestDto(
                    id = it,
                )
            },
            medarbeider = null
        )

        val fristDato = LocalDate.now()

        val nokkelord = getNokkelord(behandling)

        val updateGosysOppgaveRequest = if (representerer == null) {
            if (nokkelord == null) {
                UpdateGosysOppgaveOnCompletedBehandlingRequestWithoutRepresenterer(
                    meta = PatchMetaWithKommentar(
                        versjon = currentGosysOppgave.versjon,
                        kommentar = kommentar,
                    ),
                    fristDato = fristDato,
                    fordeling = fordeling,
                )
            } else {
                UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithoutRepresenterer(
                    meta = PatchMetaWithKommentar(
                        versjon = currentGosysOppgave.versjon,
                        kommentar = kommentar,
                    ),
                    fristDato = fristDato,
                    fordeling = fordeling,
                    nokkelord = nokkelord
                )
            }
        } else {
            if (nokkelord == null) {
                UpdateGosysOppgaveOnCompletedBehandlingRequestWithRepresenterer(
                    meta = PatchMetaWithKommentarAndRepresenterer(
                        versjon = currentGosysOppgave.versjon,
                        kommentar = kommentar,
                        representerer = representerer,
                    ),
                    fristDato = fristDato,
                    fordeling = fordeling,
                )
            } else {
                UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithRepresenterer(
                    meta = PatchMetaWithKommentarAndRepresenterer(
                        versjon = currentGosysOppgave.versjon,
                        kommentar = kommentar,
                        representerer = representerer,
                    ),
                    fristDato = fristDato,
                    fordeling = fordeling,
                    nokkelord = nokkelord
                )
            }
        }

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = systemContext
        )
    }

    private fun getNokkelord(behandling: Behandling): Set<String>? {
        if (behandling.shouldBeSentToTrygderetten()) {
            return null
        } else {
            return setOf(
                when (behandling.utfall) {
                    Utfall.MEDHOLD_ETTER_FVL_35 -> "Medhold"
                    Utfall.BESLUTNING_IKKE_OMGJOERE -> "Ikke omgjort"
                    Utfall.STADFESTET_ANNEN_BEGRUNNELSE -> "Stadfestet"
                    Utfall.UGUNST -> "Ugunst"
                    Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD -> "Medhold"
                    Utfall.GJENOPPTATT_OPPHEVET -> "Opphevet"
                    Utfall.GJENOPPTATT_STADFESTET -> "Stadfestet"

                    Utfall.INNSTILLING_STADFESTELSE, Utfall.INNSTILLING_AVVIST, Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES, Utfall.INNSTILLING_GJENOPPTAS_IKKE -> throw IllegalStateException(
                        "Wrong utfall in this case. Investigate behandling ${behandling.id}"
                    )

                    null -> throw IllegalStateException("Missing utfall in this case. Investigate behandling ${behandling.id}")

                    else -> behandling.utfall!!.navn
                }
            )
        }
    }


    fun addKommentar(
        behandling: Behandling,
        kommentar: String,
        systemContext: Boolean,
        throwExceptionIfFerdigstilt: Boolean,
    ): GosysOppgaveRecordV2? {
        logger.debug("Adding kommentar to Gosys-oppgave ${behandling.gosysOppgaveId}")
        val currentGosysOppgave = gosysOppgaveClient.getGosysOppgaveV2(
            gosysOppgaveId = behandling.gosysOppgaveId!!,
            systemContext = systemContext
        )

        if (!shouldAttemptGosysOppgaveUpdate(
                currentGosysOppgave = currentGosysOppgave,
                throwExceptionIfFerdigstilt = throwExceptionIfFerdigstilt
            )
        ) {
            return null
        }

        val representerer = getRepresenterer(systemContext = systemContext)

        val updateGosysOppgaveRequest = if (representerer == null) {
            AddKommentarToGosysOppgaveRequestWithoutRepresenterer(
                meta = PatchMetaWithKommentar(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                )
            )
        } else {
            AddKommentarToGosysOppgaveRequestWithRepresenterer(
                meta = PatchMetaWithKommentarAndRepresenterer(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                    representerer = representerer
                )
            )
        }

        return updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = updateGosysOppgaveRequest,
            systemContext = systemContext,
        )
    }

    fun avsluttGosysOppgave(
        behandling: Behandling,
        throwExceptionIfFerdigstilt: Boolean,
        systemContext: Boolean = true
    ) {
        logger.debug("Avslutter Gosys-oppgave ${behandling.gosysOppgaveId}")
        val currentGosysOppgave =
            gosysOppgaveClient.getGosysOppgaveV2(
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

        val representerer = getRepresenterer(systemContext = systemContext)

        val kommentar = "Klageinstansen har ferdigstilt behandlingen i Kabal med utfall: ${behandling.utfall!!.navn}."

        val avsluttGosysOppgaveRequest = if (representerer == null) {
            AvsluttGosysOppgaveRequestWithoutRepresenterer(
                meta = PatchMetaWithKommentar(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                ),
                status = GosysOppgaveRecordV2.StatusV2.FERDIGSTILT,
            )
        } else {
            AvsluttGosysOppgaveRequestWithRepresenterer(
                meta = PatchMetaWithKommentarAndRepresenterer(
                    versjon = currentGosysOppgave.versjon,
                    kommentar = kommentar,
                    representerer = representerer,
                ),
                status = GosysOppgaveRecordV2.StatusV2.FERDIGSTILT,
            )
        }

        updateOppgaveAndPublishEvent(
            behandling = behandling,
            updateGosysOppgaveRequest = avsluttGosysOppgaveRequest,
            systemContext = true
        )
    }

    private fun updateOppgaveAndPublishEvent(
        behandling: Behandling,
        updateGosysOppgaveRequest: UpdateOppgaveRequest,
        systemContext: Boolean,
    ): GosysOppgaveRecordV2 {
        val updatedGosysOppgave = gosysOppgaveClient.updateGosysOppgave(
            gosysOppgaveId = behandling.gosysOppgaveId!!,
            updateOppgaveInput = updateGosysOppgaveRequest,
            systemContext = systemContext,
        )

        val saksbehandlerident = behandling.tildeling?.saksbehandlerident ?: systembrukerIdent

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                GosysoppgaveEvent(
                    actor = Employee(
                        navIdent = saksbehandlerident,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(saksbehandlerident),
                    ),
                    timestamp = LocalDateTime.now(),
                    gosysOppgave = updatedGosysOppgave.toGosysOppgaveView(),
                    traceparent = currentTraceparent(),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.GOSYSOPPGAVE,
        )

        return updatedGosysOppgave
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
            throw GosysOppgaveClientException("Mappe did not contain id")
        }

        return GosysOppgaveMappeView(
            id = mappeResponse.id,
            navn = mappeResponse.navn
        )
    }

    fun getGosysOppgaveList(fnr: String, tema: Tema?): List<GosysOppgaveView> {
        val aktoerId = personService.getAktoerIdFromIdent(ident = fnr)

        val temaList = if (tema != null) {
            if (tema == Tema.MED) {
                //Legger til TRY når vi søker på MED.
                listOf(tema, Tema.TRY)
            } else {
                listOf(tema)
            }
        } else null

        val gosysOppgaveList = gosysOppgaveClient.fetchGosysOppgaveForAktoerIdAndTema(
            aktoerId = aktoerId,
            temaList = temaList,
        )

        return gosysOppgaveList.map { it.toGosysOppgaveView(systemContext = false) }
    }

    fun GosysOppgaveRecordV1.toGosysOppgaveView(systemContext: Boolean): GosysOppgaveView {
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
                try {
                    EnhetView(
                        enhetsnr = opprettetAvEnhetsnr,
                        navn = norg2Client.fetchEnhet(enhetNr = opprettetAvEnhetsnr).navn,
                    )
                } catch (exception: Exception) {
                    logger.warn("Could not fetch enhet for enhetsnr $opprettetAvEnhetsnr")
                    null
                }
            } else null,
            alreadyUsedBy = null,
        )
    }

    fun GosysOppgaveRecordV2.toGosysOppgaveView(): GosysOppgaveView {
        val tema = Tema.fromNavn(kategorisering.tema.kode)
        return GosysOppgaveView(
            id = id,
            tildeltEnhetsnr = fordeling.enhet.nr,
            endretAvEnhetsnr = endret?.av?.enhet?.nr,
            endretAv = endret?.av?.medarbeider?.navident.navIdentToSaksbehandlerView(),
            endretTidspunkt = endret?.tidspunkt,
            opprettetAv = opprettet.av?.medarbeider?.navident.navIdentToSaksbehandlerView(),
            opprettetTidspunkt = opprettet.tidspunkt,
            beskrivelse = beskrivelse,
            temaId = tema.id,
            gjelder = kategorisering.behandlingstype?.term,
            oppgavetype = kategorisering.oppgavetype.term,
            fristFerdigstillelse = fristDato,
            ferdigstiltTidspunkt = lukket?.tidspunkt,
            status = if (status == GosysOppgaveRecordV2.StatusV2.AAPEN) GosysOppgaveView.Status.AAPNET else GosysOppgaveView.Status.valueOf(
                status.name
            ),
            mappe = fordeling.mappe?.let { GosysOppgaveMappeView(id = it.id, navn = it.navn) },
            editable = isEditable(),
            opprettetAvEnhet = opprettet.av?.enhet?.let {
                EnhetView(
                    enhetsnr = it.nr,
                    navn = norg2Client.fetchEnhet(enhetNr = it.nr).navn
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
        currentGosysOppgave: GosysOppgaveRecordV2,
        throwExceptionIfFerdigstilt: Boolean
    ): Boolean {
        val gosysOppgaveId = currentGosysOppgave.id
        return if (!currentGosysOppgave.isEditable()) {
            if (throwExceptionIfFerdigstilt) {
                throw GosysOppgaveNotEditableException("Gosys-oppgave $gosysOppgaveId kan ikke oppdateres fordi status er ${currentGosysOppgave.status}")
            } else {
                logger.warn("Gosys-oppgave $gosysOppgaveId kan ikke oppdateres, returnerer")
                false
            }
        } else true
    }

    private fun getRepresenterer(systemContext: Boolean): Representerer? =
        if (systemContext) null else {
            Representerer(
                enhet = EnhetDto(
                    nr = klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = tokenUtil.getIdent()).enhet.enhetId
                )
            )
        }
}