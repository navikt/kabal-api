package no.nav.klage.oppgave.service.distribusjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType.ANKEBEHANDLING_AVSLUTTET
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType.KLAGEBEHANDLING_AVSLUTTET
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setAvsluttet
import no.nav.klage.oppgave.domain.klage.createAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
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
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        );
        const val SYSTEMBRUKER = "SYSTEMBRUKER" //TODO ??
        const val SYSTEM_JOURNALFOERENDE_ENHET = "9999"

    }

    @Transactional
    fun avsluttBehandling(behandlingId: UUID) {
        try {
            val hovedDokumenterIkkeFerdigstilte =
                dokumentUnderArbeidRepository.findByMarkertFerdigNotNullAndFerdigstiltNullAndParentIdIsNull()
            if (hovedDokumenterIkkeFerdigstilte.isNotEmpty()) {
                logger.warn("Kunne ikke avslutte behandling $behandlingId fordi noen dokumenter mangler ferdigstilling. Prøver på nytt senere.")
                return
            }

            logger.debug("Alle vedtak i behandling $behandlingId er ferdigstilt, så vi markerer behandlingen som avsluttet")
            privateAvsluttBehandling(behandlingId)

        } catch (e: Exception) {
            logger.error("Feilet under avslutning av behandling $behandlingId. Se mer i secure log")
            secureLogger.error("Feilet under avslutning av behandling $behandlingId", e)
        }
    }

    private fun privateAvsluttBehandling(behandlingId: UUID): Behandling {
        val behandling = behandlingService.getBehandlingForUpdateBySystembruker(behandlingId)

        val hoveddokumenter =
            dokumentUnderArbeidRepository.findByMarkertFerdigNotNullAndFerdigstiltNotNullAndParentIdIsNullAndBehandlingId(
                behandlingId
            ).filter {
                it.dokumentType in listOf(
                    DokumentType.VEDTAK,
                    DokumentType.BESLUTNING
                )
            }

        val behandlingEvent = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = behandling.kildeReferanse,
            kilde = behandling.kildesystem.navn,
            kabalReferanse = behandling.currentDelbehandling().id.toString(),
            type = when (behandling.type) {
                Type.KLAGE -> KLAGEBEHANDLING_AVSLUTTET
                Type.ANKE -> ANKEBEHANDLING_AVSLUTTET
                Type.ANKE_I_TRYGDERETTEN -> TODO()
            },
            detaljer = getBehandlingDetaljer(behandling, hoveddokumenter)
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = behandlingId,
                kilde = behandling.kildesystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )

        if (behandling.currentDelbehandling().shouldBeSentToTrygderetten()) {
            //TODO: Legg inn når FE er klar til å vise disse.
            //createAnkeITrygderettenbehandling(behandling)
        }

        val event = behandling.setAvsluttet(SYSTEMBRUKER)
        applicationEventPublisher.publishEvent(event)

        return behandling
    }

    private fun createAnkeITrygderettenbehandling(behandling: Behandling) {
        logger.debug("Creating ankeITrygderettenbehandling based on behandling with id ${behandling.id}")
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
            behandling.createAnkeITrygderettenbehandlingInput()
        )
    }

    private fun getBehandlingDetaljer(
        behandling: Behandling,
        hoveddokumenter: List<DokumentUnderArbeid>
    ): BehandlingDetaljer {
        return when (behandling.type) {
            Type.KLAGE -> {
                BehandlingDetaljer(
                    klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.avsluttetAvSaksbehandler!!,
                        utfall = ExternalUtfall.valueOf(behandling.currentDelbehandling().utfall!!.name),
                        journalpostReferanser = hoveddokumenter.mapNotNull { it.journalpostId }
                    )
                )
            }
            Type.ANKE -> {
                BehandlingDetaljer(
                    ankebehandlingAvsluttet = AnkebehandlingAvsluttetDetaljer(
                        avsluttet = behandling.avsluttetAvSaksbehandler!!,
                        utfall = ExternalUtfall.valueOf(behandling.currentDelbehandling().utfall!!.name),
                        journalpostReferanser = hoveddokumenter.mapNotNull { it.journalpostId }
                    )
                )
            }
            Type.ANKE_I_TRYGDERETTEN -> TODO()
        }
    }
}