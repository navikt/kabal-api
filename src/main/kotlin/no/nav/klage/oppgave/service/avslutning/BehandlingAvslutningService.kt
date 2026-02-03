package no.nav.klage.oppgave.service.avslutning

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.*
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.setters.BehandlingSetters.setAvsluttet
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType.*
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
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
    private val gjenopptakITrygderettenbehandlingService: GjenopptakITrygderettenbehandlingService,
    private val ankebehandlingService: AnkebehandlingService,
    private val fssProxyClient: KlageFssProxyClient,
    private val gosysOppgaveService: GosysOppgaveService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val gjenopptaksbehandlingService: GjenopptaksbehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapperBehandlingEvents = jacksonObjectMapper()
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
            logger.error("Feilet under avslutning av behandling $behandlingId. Se mer i team-logs")
            teamLogger.error("Feilet under avslutning av behandling $behandlingId", e)
            throw e
        }
    }

    //NB: I denne løsningen er gosysoppgave knyttet til Infotrygd-fagsystem, med unntak av Omgjøringskrav basert på journalpost. Vurder hvordan denne koblingen bør være.
    private fun privateAvsluttBehandling(behandlingId: UUID): Behandling {
        val behandling = behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(behandlingId)

        if (behandling.ferdigstilling?.avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }

        when (behandling) {
            is Klagebehandling -> handleKlagebehandling(behandling)
            is Ankebehandling -> handleAnkebehandling(behandling)
            is AnkeITrygderettenbehandling -> handleAnkeITrygderettenbehandling(behandling)
            is BehandlingEtterTrygderettenOpphevet -> handleBehandlingEtterTrygderettenOpprettet(behandling)
            is Omgjoeringskravbehandling -> handleOmgjoeringskravbehandling(behandling)
            is Gjenopptaksbehandling -> handleGjenopptaksbehandling(behandling)
            is GjenopptakITrygderettenbehandling -> handleGjenopptakITrygderettenbehandling(behandling)
        }

        val event = behandling.setAvsluttet(systembrukerIdent)
        applicationEventPublisher.publishEvent(event)

        return behandling
    }

    private fun handleKlagebehandling(klagebehandling: Klagebehandling) {
        if (klagebehandling.fagsystem == Fagsystem.IT01) {
            logger.debug("Klage med id ${klagebehandling.id} kommer fra Infotrygd, oppdaterer der.")
            updateInfotrygd(klagebehandling)
        } else if (!klagebehandling.gosysOppgaveRequired) {
            logger.debug("Klage med id ${klagebehandling.id} kommer fra modernisert fagsystem, lager Kafka-melding.")
            createKafkaEventForModernizedFagsystem(klagebehandling)
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand på klagebehandling med id ${klagebehandling.id}. Undersøk.")
        }

        if (klagebehandling.gosysOppgaveRequired) {
            if (klagebehandling.gosysOppgaveId != null && klagebehandling.gosysOppgaveUpdate != null && !klagebehandling.ignoreGosysOppgave) {
                logger.debug("Klage med id ${klagebehandling.id} har Gosys-oppgave, oppdaterer den.")
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = klagebehandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
        }
    }

    private fun handleAnkebehandling(ankebehandling: Ankebehandling) {
        if (ankebehandling.shouldBeSentToTrygderetten()) {
            logger.debug("Anke med id ${ankebehandling.id} sendes til trygderetten. Oppretter AnkeITrygderettenbehandling.")
            createAnkeITrygderettenbehandling(ankebehandling)
            if (ankebehandling.fagsystem == Fagsystem.IT01) {
                logger.debug("Vi informerer Infotrygd om innstilling til Trygderetten fra anke med id ${ankebehandling.id}")
                fssProxyClient.setToFinishedWithAppAccess(
                    sakId = ankebehandling.kildeReferanse,
                    SakFinishedInput(
                        status = SakFinishedInput.Status.VIDERESENDT_TR,
                        nivaa = SakFinishedInput.Nivaa.KA,
                        typeResultat = SakFinishedInput.TypeResultat.INNSTILLING_2,
                        utfall = SakFinishedInput.Utfall.valueOf(ankeutfallToInfotrygdutfall[ankebehandling.utfall!!]!!),
                        mottaker = SakFinishedInput.Mottaker.TRYGDERETTEN,
                        saksbehandlerIdent = ankebehandling.tildeling!!.saksbehandlerident!!
                    )
                )
                logger.debug("Vi har informert Infotrygd om innstilling til Trygderetten.")
            }
            //No need for notifying modernized fagsystem when sending to Trygderetten.
        } else if (ankebehandling.fagsystem == Fagsystem.IT01) {
            logger.debug("Anke med id ${ankebehandling.id} kommer fra Infotrygd, oppdaterer der.")
            updateInfotrygd(ankebehandling)
        } else if (!ankebehandling.gosysOppgaveRequired) {
            logger.debug("Anke med id ${ankebehandling.id} kommer fra modernisert fagsystem, lager Kafka-melding.")
            createKafkaEventForModernizedFagsystem(ankebehandling)
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand på ankebehandling med id ${ankebehandling.id}. Undersøk.")
        }
        if (ankebehandling.gosysOppgaveRequired) {
            if (ankebehandling.gosysOppgaveId != null && ankebehandling.gosysOppgaveUpdate != null && !ankebehandling.ignoreGosysOppgave) {
                logger.debug("Anke med id ${ankebehandling.id} har Gosys-oppgave, oppdaterer den.")
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = ankebehandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
        }
    }

    private fun handleAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        if (ankeITrygderettenbehandling.shouldCreateNewAnkebehandling()) {
            logger.debug("Oppretter ny Ankebehandling basert på AnkeITrygderettenbehandling fra ankeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id}")
            createNewAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling)
            if (ankeITrygderettenbehandling.gosysOppgaveRequired) {
                logger.debug("AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
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
        } else if (ankeITrygderettenbehandling.shouldCreateNewBehandlingEtterTROpphevet()) {
            logger.debug("Oppretter ny behandling, etter TR opphevet, basert på AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id}")
            createNewBehandlingEtterTROpphevetFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling)
            if (ankeITrygderettenbehandling.gosysOppgaveRequired) {
                logger.debug("AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
                val kommentar =
                    "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken."

                gosysOppgaveService.addKommentar(
                    behandling = ankeITrygderettenbehandling,
                    kommentar = kommentar,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else if (ankeITrygderettenbehandling.fagsystem == Fagsystem.IT01) {
            logger.debug("AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id} kommer fra Infotrygd, oppdaterer der.")
            updateInfotrygd(ankeITrygderettenbehandling)
        } else if (!ankeITrygderettenbehandling.gosysOppgaveRequired) {
            logger.debug("AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id} kommer fra modernisert fagsystem, lager Kafka-melding.")
            createKafkaEventForModernizedFagsystem(ankeITrygderettenbehandling)
        } else if (ankeITrygderettenbehandling.shouldNotCreateNewBehandling()) {
            logger.debug("AnkeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id} skal tilbake til vedtaksinstans med Gosys-oppgave.")
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand på ankeITrygderettenbehandling med id ${ankeITrygderettenbehandling.id}. Undersøk.")
        }

        if (ankeITrygderettenbehandling.gosysOppgaveRequired && ankeITrygderettenbehandling.shouldNotCreateNewBehandling()) {
            if (ankeITrygderettenbehandling.gosysOppgaveId != null && ankeITrygderettenbehandling.gosysOppgaveUpdate != null && !ankeITrygderettenbehandling.ignoreGosysOppgave) {
                logger.debug("AnkeITrygderetten med id ${ankeITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = ankeITrygderettenbehandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
        }
    }

    private fun handleBehandlingEtterTrygderettenOpprettet(behandlingEtterTrygderettenOpphevet: BehandlingEtterTrygderettenOpphevet) {
        if (behandlingEtterTrygderettenOpphevet.fagsystem == Fagsystem.IT01) {
            logger.debug("BehandlingEtterTrygderettenOpphevet med id ${behandlingEtterTrygderettenOpphevet.id} kommer fra Infotrygd, oppdaterer der.")
            updateInfotrygd(behandlingEtterTrygderettenOpphevet)
        } else if (!behandlingEtterTrygderettenOpphevet.gosysOppgaveRequired) {
            logger.debug("BehandlingEtterTrygderettenOpphevet med id ${behandlingEtterTrygderettenOpphevet.id} kommer fra modernisert fagsystem, lager Kafka-melding.")
            createKafkaEventForModernizedFagsystem(behandlingEtterTrygderettenOpphevet)
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand på behandlingEtterTrygderettenOpphevet med id ${behandlingEtterTrygderettenOpphevet.id}. Undersøk.")
        }

        if (behandlingEtterTrygderettenOpphevet.gosysOppgaveRequired) {
            if (behandlingEtterTrygderettenOpphevet.gosysOppgaveId != null && behandlingEtterTrygderettenOpphevet.gosysOppgaveUpdate != null && !behandlingEtterTrygderettenOpphevet.ignoreGosysOppgave) {
                logger.debug("BehandlingEtterTrygderettenOpphevet med id ${behandlingEtterTrygderettenOpphevet.id} har Gosys-oppgave, oppdaterer den.")
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = behandlingEtterTrygderettenOpphevet,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
        }
    }

    private fun handleOmgjoeringskravbehandling(omgjoeringskravbehandling: Omgjoeringskravbehandling) {
        if (omgjoeringskravbehandling.shouldBeSentToVedtaksinstans()) {
            logger.debug("Omgjøringskravbehandling med id ${omgjoeringskravbehandling.id} har utfall som skal formidles til førsteinstans.")
            if (omgjoeringskravbehandling.gosysOppgaveRequired) {
                if (!omgjoeringskravbehandling.ignoreGosysOppgave) {
                    logger.debug("Omgjøringskravbehandling med id ${omgjoeringskravbehandling.id} har Gosys-oppgave, oppdaterer den.")
                    gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                        behandling = omgjoeringskravbehandling,
                        systemContext = true,
                        throwExceptionIfFerdigstilt = true,
                    )
                } else {
                    logger.debug("Omgjøringskravbehandling med id ${omgjoeringskravbehandling.id} har Gosys-oppgave, men den skal ignoreres.")
                }
            } else {
                logger.debug("Omgjøringskravbehandling med id ${omgjoeringskravbehandling.id} er basert på Kabal-behandling, og resultatet skal formidles til førsteinstans via en Kafka-melding.")
                createKafkaEventForModernizedFagsystem(omgjoeringskravbehandling)
            }
        } else if (omgjoeringskravbehandling.shouldBeCompletedInKA()) {
            logger.debug("Avslutter omgjøringskravbehandling med id ${omgjoeringskravbehandling.id} med utfall som ikke skal formidles til førsteinstans.")
            if (omgjoeringskravbehandling.gosysOppgaveRequired) {
                logger.debug("Avslutter oppgave i Gosys for omgjøringskravbehandling med id ${omgjoeringskravbehandling.id}.")
                gosysOppgaveService.avsluttGosysOppgave(
                    behandling = omgjoeringskravbehandling,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand i behandling med id ${omgjoeringskravbehandling.id}, undersøk.")
        }
    }

    private fun handleGjenopptaksbehandling(gjenopptaksbehandling: Gjenopptaksbehandling) {
        if (gjenopptaksbehandling.shouldBeSentToVedtaksinstans()) {
            logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} har utfall som skal formidles til førsteinstans.")
            if (gjenopptaksbehandling.gosysOppgaveRequired) {
                if (!gjenopptaksbehandling.ignoreGosysOppgave) {
                    logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} har Gosys-oppgave, oppdaterer den.")
                    gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                        behandling = gjenopptaksbehandling,
                        systemContext = true,
                        throwExceptionIfFerdigstilt = true,
                    )
                } else {
                    logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} har Gosys-oppgave, men den skal ignoreres.")
                }
            } else {
                logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} er basert på Kabal-behandling, og resultatet skal formidles til førsteinstans via en Kafka-melding.")
                createKafkaEventForModernizedFagsystem(gjenopptaksbehandling)
            }
        } else if (gjenopptaksbehandling.shouldBeSentToTrygderetten()) {
            createGjenopptakITrygderettenbehandling(gjenopptaksbehandling)
            if (gjenopptaksbehandling.gosysOppgaveRequired) {
                if (gjenopptaksbehandling.gosysOppgaveId != null && gjenopptaksbehandling.gosysOppgaveUpdate != null && !gjenopptaksbehandling.ignoreGosysOppgave) {
                    logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} har Gosys-oppgave, oppdaterer den.")
                    gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                        behandling = gjenopptaksbehandling,
                        systemContext = true,
                        throwExceptionIfFerdigstilt = true,
                    )
                }
            }
        } else if (gjenopptaksbehandling.shouldBeCompletedInKA()) {
            //Saken avsluttes ved "Trukket" og "Henlagt"
            logger.debug("Gjenopptaksbehandling med id ${gjenopptaksbehandling.id} er avsluttet med utfall som ikke fører til videre behandling.")
            if (gjenopptaksbehandling.gosysOppgaveRequired) {
                logger.debug("Avslutter oppgave i Gosys for gjenopptaksbehandling med id ${gjenopptaksbehandling.id}.")
                gosysOppgaveService.avsluttGosysOppgave(
                    behandling = gjenopptaksbehandling,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else {
            throw BehandlingAvsluttetException("Ugyldig tilstand i behandling med id ${gjenopptaksbehandling.id}, undersøk.")
        }
    }

    private fun handleGjenopptakITrygderettenbehandling(gjenopptakITrygderettenbehandling: GjenopptakITrygderettenbehandling) {
        if (gjenopptakITrygderettenbehandling.shouldCreateNewGjenopptaksbehandling()) {
            logger.debug("Oppretter ny Gjenopptaksbehandling basert på GjenopptakITrygderettenbehandling fra gjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id}")
            createNewGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(gjenopptakITrygderettenbehandling)
            if (gjenopptakITrygderettenbehandling.gosysOppgaveRequired) {
                logger.debug("AnkeITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
                val kommentar = if (gjenopptakITrygderettenbehandling.nyGjenopptaksbehandlingKA != null) {
                    "Klageinstansen har opprettet ny behandling i Kabal."
                } else {
                    error("Ugyldig tilstand for å opprette ny ankebehandling fra anke i Trygderetten")
                }

                gosysOppgaveService.addKommentar(
                    behandling = gjenopptakITrygderettenbehandling,
                    kommentar = kommentar,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else if (gjenopptakITrygderettenbehandling.shouldCreateNewBehandlingEtterTROpphevet()) {
            logger.debug("Oppretter ny behandling, etter TR opphevet, basert på GjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id}")
            createNewBehandlingEtterTROpphevetFromGjenopptakITrygderettenbehandling(gjenopptakITrygderettenbehandling)
            if (gjenopptakITrygderettenbehandling.gosysOppgaveRequired) {
                logger.debug("GjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
                val kommentar =
                    "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken."

                gosysOppgaveService.addKommentar(
                    behandling = gjenopptakITrygderettenbehandling,
                    kommentar = kommentar,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else if (gjenopptakITrygderettenbehandling.shouldBeCompletedInKA()) {
            logger.debug("Avslutter gjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id} med utfall som ikke skal formidles til førsteinstans.")
            if (gjenopptakITrygderettenbehandling.gosysOppgaveRequired) {
                logger.debug("Avslutter oppgave i Gosys for gjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id}.")
                gosysOppgaveService.avsluttGosysOppgave(
                    behandling = gjenopptakITrygderettenbehandling,
                    throwExceptionIfFerdigstilt = false,
                )
            }
        } else if (!gjenopptakITrygderettenbehandling.gosysOppgaveRequired) {
            logger.debug("GjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id} kommer fra modernisert fagsystem, lager Kafka-melding.")
            createKafkaEventForModernizedFagsystem(gjenopptakITrygderettenbehandling)
        } else {
            if (gjenopptakITrygderettenbehandling.gosysOppgaveId != null && gjenopptakITrygderettenbehandling.gosysOppgaveUpdate != null && !gjenopptakITrygderettenbehandling.ignoreGosysOppgave) {
                logger.debug("GjenopptakITrygderettenbehandling med id ${gjenopptakITrygderettenbehandling.id} har Gosys-oppgave, oppdaterer den.")
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = gjenopptakITrygderettenbehandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
        }
    }


    private fun createKafkaEventForModernizedFagsystem(behandling: Behandling) {
        val hoveddokumenter =
            dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                behandling.id
            ).filter {
                it.dokumentType in listOf(
                    DokumentType.VEDTAK,
                    DokumentType.BESLUTNING
                )
            }
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
                is Gjenopptaksbehandling -> GJENOPPTAKSBEHANDLING_AVSLUTTET
                is GjenopptakITrygderettenbehandling -> GJENOPPTAKSBEHANDLING_AVSLUTTET
            },
            detaljer = getBehandlingDetaljer(behandling, hoveddokumenter)
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = behandling.id,
                kilde = behandling.fagsystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )
    }

    private fun updateInfotrygd(behandling: Behandling) {
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
            logger.debug("Behandlingen som er avsluttet ble sendt tilbake i Infotrygd.")
        }
    }

    private fun createNewAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug("Creating ankebehandling based on behandling with id {}", ankeITrygderettenbehandling.id)
        ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling)
    }

    private fun createNewBehandlingEtterTROpphevetFromAnkeITrygderettenbehandling(ankeITrygderettenbehandling: AnkeITrygderettenbehandling) {
        logger.debug(
            "Creating BehandlingEtterTrygderettenOpphevet based on behandling with id {}",
            ankeITrygderettenbehandling.id
        )
        behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(ankeITrygderettenbehandling)
    }

    private fun createNewGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(gjenopptakITrygderettenbehandling: GjenopptakITrygderettenbehandling) {
        logger.debug(
            "Creating gjenopptaksbehandling based on behandling with id {}",
            gjenopptakITrygderettenbehandling.id
        )
        gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
            gjenopptakITrygderettenbehandling
        )
    }

    private fun createNewBehandlingEtterTROpphevetFromGjenopptakITrygderettenbehandling(
        gjenopptakITrygderettenbehandling: GjenopptakITrygderettenbehandling
    ) {
        logger.debug(
            "Creating BehandlingEtterTrygderettenOpphevet based on behandling with id {}",
            gjenopptakITrygderettenbehandling.id
        )
        behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
            gjenopptakITrygderettenbehandling
        )
    }

    private fun createAnkeITrygderettenbehandling(behandling: Behandling) {
        logger.debug("Creating ankeITrygderettenbehandling based on behandling with id {}", behandling.id)
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
            behandling.createAnkeITrygderettenbehandlingInput()
        )
    }

    private fun createGjenopptakITrygderettenbehandling(behandling: Behandling) {
        logger.debug("Creating gjenopptakITrygderettenbehandling based on behandling with id {}", behandling.id)
        gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(
            behandling.createGjenopptakITrygderettenbehandlingInput()
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

            is Ankebehandling, is AnkeITrygderettenbehandling -> {
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

            is Gjenopptaksbehandling, is GjenopptakITrygderettenbehandling -> {
                BehandlingDetaljer(
                    gjenopptaksbehandlingAvsluttet = GjenopptaksbehandlingAvsluttetDetaljer(
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