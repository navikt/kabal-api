package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.service.mapper.BehandlingSkjemaV2
import no.nav.klage.oppgave.service.mapper.mapToSkjemaV2
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class BehandlingEndretKafkaProducer(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
) {
    @Value("\${BEHANDLING_ENDRET_TOPIC_V2}")
    lateinit var topicV2: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun sendBehandlingEndret(behandling: Behandling) {
        logger.debug("Sending to Kafka topic: {}", topicV2)

        val json = behandling.mapToSkjemaV2().toJson()
        secureLogger.debug("Sending to Kafka topic: {}, value: {}", topicV2, json)

        runCatching {
            val result = aivenKafkaTemplate.send(
                topicV2,
                behandling.id.toString(),
                json
            ).get()
            logger.info("${behandling.type.navn} endret sent to Kafka")
            secureLogger.debug("${behandling.type.navn} endret for behandling {} sent to kafka ({})", behandling.id, result)
        }.onFailure {
            val errorMessage =
                "Could not send ${behandling.type.navn} endret to Kafka. Need to resend behandling ${behandling.id} manually. Check secure logs for more information."
            logger.error(errorMessage)
            secureLogger.error("Could not send behandling ${behandling.id} endret to Kafka", it)
        }
    }

    fun sendBehandlingDeleted(behandlingId: UUID) {
        logger.debug("Sending null message (for delete) to Kafka topic: {}", topicV2)
        runCatching {
            val result = aivenKafkaTemplate.send(topicV2, behandlingId.toString(), null).get()
            logger.info("Behandling deleted sent to Kafka")
            secureLogger.debug("Behandling deleted sent to Kafka ({})", result)
        }.onFailure {
            val errorMessage =
                "Could not send klage deleted to Kafka. Need to resend behandling $behandlingId manually. Check secure logs for more information."
            logger.error(errorMessage)
            secureLogger.error(errorMessage, it)
        }
    }

    fun BehandlingSkjemaV2.toJson(): String = objectMapper.writeValueAsString(this)
}
