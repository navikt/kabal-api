package no.nav.klage.oppgave.service.distribusjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.KlagevedtakFattet
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setAvsluttet
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class KlagebehandlingAvslutningService(
    private val kafkaEventRepository: KafkaEventRepository,
    private val klagebehandlingService: KlagebehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
        const val SYSTEMBRUKER = "SYSTEMBRUKER" //TODO ??
        const val SYSTEM_JOURNALFOERENDE_ENHET = "9999"

    }

    @Transactional
    fun avsluttKlagebehandling(klagebehandlingId: UUID): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId)

        val journalpostId =
            kabalDocumentGateway.getJournalpostIdForHovedadressat(klagebehandling.vedtak.dokumentEnhetId!!)!!

        val eventId = UUID.randomUUID()

        val vedtakFattet = KlagevedtakFattet(
            eventId = eventId,
            kildeReferanse = klagebehandling.kildeReferanse,
            kilde = klagebehandling.kildesystem.navn,
            utfall = ExternalUtfall.valueOf(klagebehandling.vedtak.utfall!!.name),
            vedtaksbrevReferanse = journalpostId,
            kabalReferanse = klagebehandling.vedtak.id.toString()
        )

        kafkaEventRepository.save(
            KafkaEvent(
                id = eventId,
                klagebehandlingId = klagebehandlingId,
                kilde = klagebehandling.kildesystem.navn,
                kildeReferanse = klagebehandling.kildeReferanse,
                jsonPayload = vedtakFattet.toJson(),
                type = EventType.KLAGE_VEDTAK
            )
        )

        val event = klagebehandling.setAvsluttet(SYSTEMBRUKER)
        applicationEventPublisher.publishEvent(event)

        return klagebehandling
    }

    private fun Any.toJson(): String = objectMapper.writeValueAsString(this)

}