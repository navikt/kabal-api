package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.repositories.PersonProtectionRepository
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.service.mapper.BehandlingSkjemaV2
import no.nav.klage.oppgave.service.mapper.mapToSkjemaV2
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Service
class BehandlingEndretKafkaProducer(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    private val klageLookupGateway: KlageLookupGateway,
    private val sakPersongalleriRepository: SakPersongalleriRepository,
    private val personProtectionRepository: PersonProtectionRepository,
) {
    @Value($$"${BEHANDLING_ENDRET_TOPIC_V2}")
    lateinit var topicV2: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper = jacksonObjectMapper()
    }

    fun sendBehandlingEndret(behandling: Behandling) {
        logger.debug("Sending to Kafka topic: {}", topicV2)

        val persongalleriFnrList = sakPersongalleriRepository.findByFagsystemAndFagsakId(
            fagsystem = behandling.fagsystem,
            fagsakId = behandling.fagsakId,
        ).map { it.foedselsnummer }

        val allFnr = (persongalleriFnrList + behandling.sakenGjelder.partId.value).distinct()

        val protections = allFnr.mapNotNull { fnr ->
            personProtectionRepository.findByFoedselsnummer(fnr).also {
                if (it == null) {
                    logger.error("PersonProtection not found for a person in behandling {}. Giving wrong information to kabal-search. See more in team-logs", behandling.id)
                    teamLogger.error("PersonProtection not found for a person in behandling {}. Giving wrong information to kabal-search. Fnr {}", behandling.id, fnr)
                }
            }
        }

        val erStrengtFortrolig = protections.any { it.strengtFortrolig }
        val erFortrolig = protections.any { it.fortrolig }
        val erEgenAnsatt = protections.any { it.skjermet }

        val medunderskriverEnhet =
            behandling.medunderskriver?.saksbehandlerident?.let { klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = it).enhet.enhetId }

        val json = behandling.mapToSkjemaV2(
            erStrengtFortrolig = erStrengtFortrolig,
            erFortrolig = erFortrolig,
            erEgenAnsatt = erEgenAnsatt,
            medunderskriverEnhet = medunderskriverEnhet,
        ).toJson()

        runCatching {
            aivenKafkaTemplate.send(
                topicV2,
                behandling.id.toString(),
                json
            ).get()
            logger.debug("${behandling.type.navn} endret sent to Kafka.")
        }.onFailure {
            logger.error("Could not send ${behandling.type.navn} endret to Kafka. Need to resend behandling ${behandling.id}. Check team-logs for more details.")
            teamLogger.error(
                "Could not send behandling ${behandling.id} endret to Kafka. Need to resend behandling ${behandling.id}",
                it
            )
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
