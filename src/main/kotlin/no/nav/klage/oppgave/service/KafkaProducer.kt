package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class KafkaProducer(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun publishToKafkaTopic(klagebehandlingId: UUID, json: String, topic: String) {
        logger.debug("Sending to Kafka topic: {}", topic)
        runCatching {
            aivenKafkaTemplate.send(topic, klagebehandlingId.toString(), json).get()
            logger.debug("Payload sent to Kafka.")
        }.onFailure {
            val errorMessage =
                "Could not send payload to Kafka. Check team-logs for more information."
            logger.error(errorMessage)
            teamLogger.error("Could not send payload to Kafka", it)
        }
    }
}
