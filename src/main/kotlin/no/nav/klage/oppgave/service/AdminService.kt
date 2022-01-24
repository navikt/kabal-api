package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class AdminService(
    private val kafkaDispatcher: KafkaDispatcher,
    private val behandlingRepository: BehandlingRepository,
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer
) {

    companion object {
        private const val TWO_SECONDS = 2000L

        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun syncKafkaWithDb() {
        var pageable: Pageable =
            PageRequest.of(0, 50, Sort.by("created").descending())
        do {
            val behandlingPage = behandlingRepository.findAll(pageable)

            behandlingPage.content.map { behandling ->
                try {
                    if (behandling.type == Type.KLAGE) {
                        behandlingEndretKafkaProducer.sendKlageEndretV1(behandling as Klagebehandling)
                        behandlingEndretKafkaProducer.sendKlageEndretV2(behandling)
                    } else {
                        behandlingEndretKafkaProducer.sendAnkeEndretV2(behandling as Ankebehandling)
                    }
                } catch (e: Exception) {
                    logger.warn("Exception during send to Kafka", e)
                }
            }

            pageable = behandlingPage.nextPageable()
        } while (pageable.isPaged)
    }

    fun resendToDVH() {
        logger.debug("Attempting to resend all events to DVH")
        kafkaDispatcher.dispatchEventsToKafka(
            EventType.STATS_DVH,
            listOf(UtsendingStatus.IKKE_SENDT, UtsendingStatus.FEILET, UtsendingStatus.SENDT)
        )
    }
}