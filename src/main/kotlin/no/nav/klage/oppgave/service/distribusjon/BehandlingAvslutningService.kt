package no.nav.klage.oppgave.service.distribusjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentType
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setAvsluttet
import no.nav.klage.oppgave.repositories.KafkaEventRepository
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
    fun avsluttBehandling(behandlingId: UUID): Behandling {
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

        val journalpostId = null

        val eventId = UUID.randomUUID()

        val vedtakFattet = KlagevedtakFattet(
            eventId = eventId,
            kildeReferanse = behandling.kildeReferanse,
            kilde = behandling.kildesystem.navn,
            utfall = ExternalUtfall.valueOf(behandling.currentDelbehandling().utfall!!.name),
            vedtaksbrevReferanse = journalpostId,
            kabalReferanse = behandling.currentDelbehandling().id.toString()
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                klagebehandlingId = behandlingId,
                kilde = behandling.kildesystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                jsonPayload = vedtakFattet.toJson(),
                type = EventType.KLAGE_VEDTAK
            )
        )

        val behandlingEvent = BehandlingEvent(
            eventId = eventId,
            kildeReferanse = behandling.kildeReferanse,
            kilde = behandling.kildesystem.navn,
            kabalReferanse = behandling.currentDelbehandling().id.toString(),
            type = BehandlingEventType.KLAGEBEHANDLING_AVSLUTTET,
            detaljer = BehandlingDetaljer(
                klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
                    avsluttet = behandling.avsluttetAvSaksbehandler!!,
                    utfall = ExternalUtfall.valueOf(behandling.currentDelbehandling().utfall!!.name),
                    journalpostReferanser = hoveddokumenter.mapNotNull{ it.journalpostId }
                )
            )
        )

        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                klagebehandlingId = behandlingId,
                kilde = behandling.kildesystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )

        val event = behandling.setAvsluttet(SYSTEMBRUKER)
        applicationEventPublisher.publishEvent(event)

        return behandling
    }

    private fun Any.toJson(): String = objectMapper.writeValueAsString(this)

}