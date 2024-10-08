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
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setAvsluttet
import no.nav.klage.oppgave.domain.klage.createAnkeITrygderettenbehandlingInput
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
    private val oppgaveApiService: OppgaveApiService,
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
            if (behandling.fagsystem == Fagsystem.IT01) {
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


            //if fagsystem is Infotrygd also do this.
            if (behandling.fagsystem == Fagsystem.IT01 && behandling.type !in listOf(
                    Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
                )
            ) {
                logger.debug("Behandlingen som er avsluttet skal sendes tilbake til Infotrygd.")

                val sakInKlanke = fssProxyClient.getSakWithAppAccess(
                    sakId = behandling.kildeReferanse,
                    input = GetSakAppAccessInput(saksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!)
                )
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
            } else {
                //Notify modern fagsystem
                val behandlingEvent = BehandlingEvent(
                    eventId = UUID.randomUUID(),
                    kildeReferanse = behandling.kildeReferanse,
                    kilde = behandling.fagsystem.navn,
                    kabalReferanse = behandling.id.toString(),
                    type = when (behandling.type) {
                        Type.KLAGE -> KLAGEBEHANDLING_AVSLUTTET
                        Type.ANKE -> ANKEBEHANDLING_AVSLUTTET
                        Type.ANKE_I_TRYGDERETTEN -> ANKEBEHANDLING_AVSLUTTET
                        Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET_AVSLUTTET
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
            }

            if (behandling.oppgaveId != null && behandling.oppgaveReturned != null) {
                try {
                    oppgaveApiService.returnOppgave(
                        oppgaveId = behandling.oppgaveId!!,
                        tildeltEnhetsnummer = behandling.oppgaveReturned!!.oppgaveReturnedTildeltEnhetsnummer,
                        mappeId = behandling.oppgaveReturned!!.oppgaveReturnedMappeId,
                        kommentar = behandling.oppgaveReturned!!.oppgaveReturnedKommentar,
                    )
                } catch (e: Exception) {
                    logger.error("Feilet under tilbakeføring av oppgave $behandlingId.")
                }
            }
        }

        val event = behandling.setAvsluttet(systembrukerIdent)
        applicationEventPublisher.publishEvent(event)

        return behandling
    }

    private fun createNewAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug("Creating ankebehandling based on behandling with id {}", ankeITrygderettenbehandling.id)
        ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling)
    }

    private fun createNewBehandlingEtterTROpphevetFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug("Creating BehandlingEtterTrygderettenOpphevet based on behandling with id {}", ankeITrygderettenbehandling.id)
        behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(ankeITrygderettenbehandling)
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
        return when (behandling.type) {
            Type.KLAGE -> {
                BehandlingDetaljer(
                    klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            Type.ANKE -> {
                BehandlingDetaljer(
                    ankebehandlingAvsluttet = AnkebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            Type.ANKE_I_TRYGDERETTEN -> {
                BehandlingDetaljer(
                    ankebehandlingAvsluttet = AnkebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                        utfall = ExternalUtfall.valueOf(behandling.utfall!!.name),
                        journalpostReferanser = hoveddokumenter.flatMap { it.dokarkivReferences }
                            .map { it.journalpostId }
                    )
                )
            }

            Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> {
                BehandlingDetaljer(
                    behandlingEtterTrygderettenOpphevetAvsluttet = BehandlingEtterTrygderettenOpphevetAvsluttetDetaljer(
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