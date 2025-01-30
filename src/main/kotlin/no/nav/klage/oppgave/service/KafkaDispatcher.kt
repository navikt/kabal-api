package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class KafkaDispatcher(
    private val kafkaEventRepository: KafkaEventRepository,
    private val kafkaProducer: KafkaProducer,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Value("\${DVH_STATISTIKK_TOPIC}")
    lateinit var dvhTopic: String

    @Value("\${VEDTAK_FATTET_TOPIC}")
    lateinit var vedtakTopic: String

    @Value("\${BEHANDLING_EVENTS_TOPIC}")
    lateinit var behandlingEventTopic: String

    @Value("\${MIN_SIDE_MICROFRONTEND_TOPIC}")
    lateinit var minSideMicrofrontendTopic: String

    fun dispatchEventsToKafka(type: EventType, utsendingStatusList: List<UtsendingStatus>) {
        logger.debug("dispatchUnsentEventsToKafka for type: {}, and statuses: {}", type, utsendingStatusList)
        kafkaEventRepository.getAllByStatusInAndTypeOrderByCreated(utsendingStatusList, type)
            .forEach { event ->
                runCatching {
                    kafkaProducer.publishToKafkaTopic(
                        topic = type.toTopic(),
                        klagebehandlingId = event.behandlingId,
                        json = event.jsonPayload
                    )
                }.onFailure {
                    event.status = UtsendingStatus.FEILET
                    event.errorMessage = it.message
                    logger.error("Send $type event ${event.id} to kafka failed, see secure log for details")
                    secureLogger.error("Send $type event ${event.id} to kafka failed. Object: $event")
                }.onSuccess {
                    event.status = UtsendingStatus.SENDT
                    event.errorMessage = null
                }
            }
    }

    private fun EventType.toTopic(): String =
        when (this) {
            EventType.KLAGE_VEDTAK -> vedtakTopic
            EventType.STATS_DVH -> dvhTopic
            EventType.BEHANDLING_EVENT -> behandlingEventTopic
            EventType.MINSIDE_MICROFRONTEND_EVENT -> minSideMicrofrontendTopic
        }
}