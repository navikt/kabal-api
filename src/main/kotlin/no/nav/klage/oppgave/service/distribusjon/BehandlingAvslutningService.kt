package no.nav.klage.oppgave.service.distribusjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.*
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType.*
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setAvsluttet
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class BehandlingAvslutningService(
    private val kafkaEventRepository: KafkaEventRepository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
    private val behandlingEtterTrygderettenOpphevetService: BehandlingEtterTrygderettenOpphevetService,
    private val ankebehandlingService: AnkebehandlingService,
    private val fssProxyClient: KlageFssProxyClient,
    private val gosysOppgaveService: GosysOppgaveService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    @Transactional
    fun avsluttBehandling(behandlingId: UUID) {
        try {
            val hovedDokumenterIkkeFerdigstilteOnBehandling =
                dokumentUnderArbeidCommonService.findHoveddokumenterOnBehandlingByMarkertFerdigNotNullAndFerdigstiltNull(
                    behandlingId = behandlingId
                )
            if (hovedDokumenterIkkeFerdigstilteOnBehandling.isNotEmpty()) {
                logger.warn(
                    "Kunne ikke avslutte behandling {} fordi noen dokumenter mangler ferdigstilling. Prøver på nytt senere.",
                    behandlingId
                )
                return
            }

            logger.debug(
                "Alle dokumenter i behandling {} er ferdigstilte, så vi kan markere behandlingen som avsluttet",
                behandlingId
            )
            privateAvsluttBehandling(behandlingId)

        } catch (e: Exception) {
            logger.error("Feilet under avslutning av behandling $behandlingId. Se mer i secure log")
            secureLogger.error("Feilet under avslutning av behandling $behandlingId", e)
            throw e
        }
    }

    private fun privateAvsluttBehandling(behandlingId: UUID): Behandling {
        val behandling = behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(behandlingId)

        if (behandling.ferdigstilling?.avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }

        if (behandling is Ankebehandling && behandling.shouldBeSentToTrygderetten()) {
            logger.debug("Anken sendes til trygderetten. Oppretter AnkeITrygderettenbehandling.")
            createAnkeITrygderettenbehandling(behandling)
            //if fagsystem is Infotrygd also do this.
            if (behandling.shouldUpdateInfotrygd()) {
                logger.debug("Vi informerer Infotrygd om innstilling til Trygderetten.")
                fssProxyClient.setToFinishedWithAppAccess(
                    sakId = behandling.kildeReferanse,
                    SakFinishedInput(
                        status = SakFinishedInput.Status.VIDERESENDT_TR,
                        nivaa = SakFinishedInput.Nivaa.KA,
                        typeResultat = SakFinishedInput.TypeResultat.INNSTILLING_2,
                        utfall = SakFinishedInput.Utfall.valueOf(ankeutfallToInfotrygdutfall[behandling.utfall!!]!!),
                        mottaker = SakFinishedInput.Mottaker.TRYGDERETTEN,
                        saksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!
                    )
                )
                logger.debug("Vi har informert Infotrygd om innstilling til Trygderetten.")
            }

        } else if (behandling is AnkeITrygderettenbehandling && behandling.shouldCreateNewAnkebehandling()) {
            logger.debug("Oppretter ny Ankebehandling basert på AnkeITrygderettenbehandling")
            createNewAnkebehandlingFromAnkeITrygderettenbehandling(behandling)
        } else if (behandling is AnkeITrygderettenbehandling && behandling.shouldCreateNewBehandlingEtterTROpphevet()) {
            logger.debug("Oppretter ny behandling, etter TR opphevet, basert på AnkeITrygderettenbehandling")
            createNewBehandlingEtterTROpphevetFromAnkeITrygderettenbehandling(behandling)
        } else if (behandling is Omgjoeringskravbehandling && behandling.utfall != Utfall.MEDHOLD_ETTER_FVL_35) {
            logger.debug("Avslutter omgjøringskravbehandling med utfall som ikke skal formidles til førsteinstans.")
            if (behandling.gosysOppgaveId != null) {
                logger.debug("Avslutter oppgave i Gosys.")
                gosysOppgaveService.avsluttGosysOppgave(
                    behandling = behandling,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else {
            val hoveddokumenter =
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    behandlingId
                ).filter {
                    it.dokumentType in listOf(
                        DokumentType.VEDTAK,
                        DokumentType.BESLUTNING
                    )
                }

            if (behandling.shouldUpdateInfotrygd()) {
                logger.debug("Behandlingen som er avsluttet skal sendes tilbake til Infotrygd.")

                val sakInKlanke = fssProxyClient.getSakWithAppAccess(
                    sakId = behandling.kildeReferanse,
                    input = GetSakAppAccessInput(saksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!)
                )

                if (sakInKlanke.typeResultat == SakFinishedInput.TypeResultat.RESULTAT.name &&
                    sakInKlanke.nivaa == SakFinishedInput.Nivaa.KA.name
                ) {
                    logger.warn("Behandlingen er allerede satt til ferdig i Infotrygd, så trenger ikke å oppdatere.")
                } else {
                    val utfall = if (sakInKlanke.sakstype != null && sakInKlanke.sakstype == "KLAGE_TILBAKEBETALING") {
                        klageTilbakebetalingutfallToInfotrygdutfall[behandling.utfall!!]!!
                    } else {
                        klageutfallToInfotrygdutfall[behandling.utfall!!]!!
                    }

                    fssProxyClient.setToFinishedWithAppAccess(
                        sakId = behandling.kildeReferanse,
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.valueOf(utfall),
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!
                        )
                    )
                    logger.debug("Behandlingen som er avsluttet ble sendt tilbake til Infotrygd.")
                }
            } else if (behandling !is OmgjoeringskravbehandlingBasedOnJournalpost || behandling.fagsystem == Fagsystem.IT01) {
                //Notify modern fagsystem
                val behandlingEvent = BehandlingEvent(
                    eventId = UUID.randomUUID(),
                    kildeReferanse = behandling.kildeReferanse,
                    kilde = behandling.fagsystem.navn,
                    kabalReferanse = behandling.id.toString(),
                    type = when (behandling) {
                        is Klagebehandling -> KLAGEBEHANDLING_AVSLUTTET
                        is Ankebehandling -> ANKEBEHANDLING_AVSLUTTET
                        is AnkeITrygderettenbehandling -> ANKEBEHANDLING_AVSLUTTET
                        is BehandlingEtterTrygderettenOpphevet -> BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET_AVSLUTTET
                        is Omgjoeringskravbehandling -> OMGJOERINGSKRAVBEHANDLING_AVSLUTTET
                    },
                    detaljer = getBehandlingDetaljer(behandling, hoveddokumenter)
                )
                kafkaEventRepository.save(
                    KafkaEvent(
                        id = UUID.randomUUID(),
                        behandlingId = behandlingId,
                        kilde = behandling.fagsystem.navn,
                        kildeReferanse = behandling.kildeReferanse,
                        jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                        type = EventType.BEHANDLING_EVENT
                    )
                )
            } else {
                logger.debug("Behandling er basert på journalpost, så vi trenger ikke å sende melding til fagsystem.")
            }
        }

        if (behandling.gosysOppgaveId != null && behandling.gosysOppgaveUpdate != null && !behandling.ignoreGosysOppgave) {
            gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                behandling = behandling,
                systemContext = true,
                throwExceptionIfFerdigstilt = true,
            )
        }

        val event = behandling.setAvsluttet(systembrukerIdent)
        applicationEventPublisher.publishEvent(event)

        return behandling
    }

    private fun createNewAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug("Creating ankebehandling based on behandling with id {}", ankeITrygderettenbehandling.id)
        ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling)

        if (ankeITrygderettenbehandling.gosysOppgaveId != null) {
            val kommentar = if (ankeITrygderettenbehandling.nyAnkebehandlingKA != null) {
                "Klageinstansen har opprettet ny behandling i Kabal."
            } else if (ankeITrygderettenbehandling.utfall == Utfall.HENVIST) {
                "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten har henvist saken."
            } else {
                error("Ugyldig tilstand for å opprette ny ankebehandling fra anke i Trygderetten")
            }

            gosysOppgaveService.addKommentar(
                behandling = ankeITrygderettenbehandling,
                kommentar = kommentar,
                systemContext = true,
                throwExceptionIfFerdigstilt = false,
            )
        }
    }

    private fun createNewBehandlingEtterTROpphevetFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug(
            "Creating BehandlingEtterTrygderettenOpphevet based on behandling with id {}",
            ankeITrygderettenbehandling.id
        )
        behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(ankeITrygderettenbehandling)

        if (ankeITrygderettenbehandling.gosysOppgaveId != null) {
            val kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken."

            gosysOppgaveService.addKommentar(
                behandling = ankeITrygderettenbehandling,
                kommentar = kommentar,
                systemContext = true,
                throwExceptionIfFerdigstilt = false,
            )
        }
    }

    private fun createAnkeITrygderettenbehandling(behandling: Behandling) {
        logger.debug("Creating ankeITrygderettenbehandling based on behandling with id {}", behandling.id)
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
            behandling.createAnkeITrygderettenbehandlingInput()
        )
    }

    private fun getBehandlingDetaljer(
        behandling: Behandling,
        hoveddokumenter: List<DokumentUnderArbeidAsHoveddokument>
    ): BehandlingDetaljer {
        return when (behandling) {
            is Klagebehandling -> {
                BehandlingDetaljer(
                    klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            is Ankebehandling -> {
                BehandlingDetaljer(
                    ankebehandlingAvsluttet = AnkebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            is AnkeITrygderettenbehandling -> {
                BehandlingDetaljer(
                    ankebehandlingAvsluttet = AnkebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            is BehandlingEtterTrygderettenOpphevet -> {
                BehandlingDetaljer(
                    behandlingEtterTrygderettenOpphevetAvsluttet = BehandlingEtterTrygderettenOpphevetAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            is Omgjoeringskravbehandling -> {
                BehandlingDetaljer(
                    omgjoeringskravbehandlingAvsluttet = OmgjoeringskravbehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }
        }
    }
}