package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.service.mapper.BehandlingSkjemaV2
import no.nav.klage.oppgave.service.mapper.mapToSkjemaV2
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class BehandlingEndretKafkaProducer(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    private val personService: PersonService,
    private val egenAnsattService: EgenAnsattService,
) {
    @Value("\${BEHANDLING_ENDRET_TOPIC_V2}")
    lateinit var topicV2: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun sendBehandlingEndret(behandling: Behandling) {
        logger.debug("Sending to Kafka topic: {}", topicV2)
        val personInfo = if (behandling.sakenGjelder.erPerson()) {
            personService.getPersonInfo(fnr = behandling.sakenGjelder.partId.value)
        } else null

        val erStrengtFortrolig = personInfo?.harBeskyttelsesbehovStrengtFortrolig() ?: false
        val erFortrolig = personInfo?.harBeskyttelsesbehovFortrolig() ?: false
        val erEgenAnsatt = personInfo?.let { egenAnsattService.erEgenAnsatt(foedselsnr = it.foedselsnr) } ?: false

        val json = behandling.mapToSkjemaV2(
            erStrengtFortrolig = erStrengtFortrolig,
            erFortrolig = erFortrolig,
            erEgenAnsatt = erEgenAnsatt
        ).toJson()

        runCatching {
            val result = aivenKafkaTemplate.send(
                topicV2,
                behandling.id.toString(),
                json
            ).get()
            logger.debug("${behandling.type.navn} endret sent to Kafka.")
        }.onFailure {
            logger.error("Could not send ${behandling.type.navn} endret to Kafka. Need to resend behandling ${behandling.id}. Check team-logs for more details.")
            teamLogger.error("Could not send behandling ${behandling.id} endret to Kafka. Need to resend behandling ${behandling.id}", it)
        }
    }

    fun sendBehandlingDeleted(behandlingId: UUID) {
        logger.debug("Sending null message (for delete) to Kafka topic: {}", topicV2)
        runCatching {
            aivenKafkaTemplate.send(topicV2, behandlingId.toString(), null).get()
            logger.debug("Behandling deleted sent to Kafka.")
        }.onFailure {
            logger.error("Could not send klage deleted to Kafka. Need to resend behandling $behandlingId. Check team-logs for more details.")
            teamLogger.error("Could not send klage deleted to Kafka. Need to resend behandling $behandlingId", it)
        }
    }

    fun BehandlingSkjemaV2.toJson(): String = objectMapper.writeValueAsString(this)
}
