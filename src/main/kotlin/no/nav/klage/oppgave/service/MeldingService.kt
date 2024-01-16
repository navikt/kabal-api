package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import no.nav.klage.oppgave.domain.kafka.BaseEvent
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.domain.kafka.MeldingEvent
import no.nav.klage.oppgave.domain.klage.Melding
import no.nav.klage.oppgave.exceptions.MeldingNotFoundException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.MeldingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class MeldingService(
    private val meldingRepository: MeldingRepository,
    private val behandlingRepository: BehandlingRepository,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    fun addMelding(
        behandlingId: UUID,
        innloggetIdent: String,
        text: String
    ): Melding {
        logger.debug("saving new melding by $innloggetIdent")

        val melding = meldingRepository.save(
            Melding(
                text = text,
                behandlingId = behandlingId,
                saksbehandlerident = innloggetIdent,
                created = LocalDateTime.now(),
            )
        )

        publishInternalEvent(
            melding = melding,
            utfoerendeIdent = innloggetIdent,
            utfoerendeName = saksbehandlerService.getNameForIdent(innloggetIdent),
            timestamp = melding.modified ?: melding.created,
        )

        return melding
    }

    fun deleteMelding(
        behandlingId: UUID,
        innloggetIdent: String,
        meldingId: UUID
    ) {
        try {
            val melding = meldingRepository.getReferenceById(meldingId)
            validateRightsToDeleteMelding(melding, innloggetIdent)

            meldingRepository.delete(melding)

            logger.debug("melding ({}) deleted by {}", meldingId, innloggetIdent)

//            publishInternalEvent(melding = melding, type = "message_deleted")
        } catch (enfe: EntityNotFoundException) {
            throw MeldingNotFoundException("couldn't find melding with id $meldingId")
        }
    }

    fun modifyMelding(
        behandlingId: UUID,
        innloggetIdent: String,
        meldingId: UUID,
        text: String
    ): Melding {
        try {
            val melding = meldingRepository.getReferenceById(meldingId)
            validateRightsToModifyMelding(melding, innloggetIdent)

            melding.text = text
            melding.modified = LocalDateTime.now()

            meldingRepository.save(melding)
            logger.debug("melding ({}) modified by {}", meldingId, innloggetIdent)

//            publishInternalEvent(melding = melding, type = "message_modified")

            return melding
        } catch (enfe: EntityNotFoundException) {
            throw MeldingNotFoundException("couldn't find melding with id $meldingId")
        }
    }

    fun getMeldingerForBehandling(behandlingId: UUID) =
        meldingRepository.findByBehandlingIdOrderByCreatedDesc(behandlingId)

    private fun validateRightsToModifyMelding(melding: Melding, innloggetIdent: String) {
        if (melding.saksbehandlerident != innloggetIdent) {
            throw MissingTilgangException(
                "Saksbehandler ($innloggetIdent) is not the author of melding (${melding.id}), and is not allowed to modify it."
            )
        }
    }

    private fun validateRightsToDeleteMelding(melding: Melding, innloggetIdent: String) {
        val behandling = behandlingRepository.getReferenceById(melding.behandlingId)

        if (behandling.tildeling?.saksbehandlerident == innloggetIdent || melding.saksbehandlerident == innloggetIdent) {
            return
        } else {
            throw MissingTilgangException(
                "Saksbehandler ($innloggetIdent) is not allowed to delete melding ${melding.id}."
            )
        }
    }

    //TODO other types
    private fun publishInternalEvent(
        melding: Melding,
        utfoerendeIdent: String,
        utfoerendeName: String,
        timestamp: LocalDateTime,
    ) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = melding.behandlingId.toString(),
                type = InternalEventType.MESSAGE,
                data = objectMapper.writeValueAsString(
                    MeldingEvent(
                        actor = BaseEvent.Actor(navIdent = utfoerendeIdent, name = utfoerendeName),
                        timestamp = timestamp,
                        id = melding.id.toString(),
                        text = melding.text,
                    )
                )
            )
        )
    }
}