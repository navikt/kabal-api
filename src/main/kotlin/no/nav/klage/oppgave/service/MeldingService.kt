package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.mapper.MeldingMapper
import no.nav.klage.oppgave.api.view.MeldingModified
import no.nav.klage.oppgave.api.view.MeldingView
import no.nav.klage.oppgave.domain.behandling.subentities.Melding
import no.nav.klage.oppgave.domain.kafka.Employee
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.domain.kafka.MeldingEvent
import no.nav.klage.oppgave.exceptions.IllegalOperation
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
    private val meldingMapper: MeldingMapper,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    fun addMelding(
        behandlingId: UUID,
        innloggetIdent: String,
        text: String,
        notify: Boolean,
    ): MeldingView {
        logger.debug("saving new melding by $innloggetIdent")

        val melding = meldingRepository.save(
            Melding(
                text = text,
                behandlingId = behandlingId,
                saksbehandlerident = innloggetIdent,
                created = LocalDateTime.now(),
                notify = notify,
            )
        )

        publishInternalEvent(
            melding = melding,
            utfoerendeIdent = innloggetIdent,
            utfoerendeName = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
            timestamp = melding.modified ?: melding.created,
            type = InternalEventType.MESSAGE,
        )

        val behandling = behandlingRepository.getReferenceById(behandlingId)

        if (notify && behandling.tildeling?.saksbehandlerident != null) {
            publishNotificationEvent(
                melding = melding,
                utfoerendeIdent = innloggetIdent,
                utfoerendeName = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                tildeltSaksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!,
                behandlingType = behandling.type,
                saksnummer = behandling.fagsakId,
                ytelse = behandling.ytelse,
            )
        }

        return meldingMapper.toMeldingView(melding)
    }

    fun notifyMelding(
        behandlingId: UUID,
        innloggetIdent: String,
        meldingId: UUID,
    ): MeldingModified {
        try {
            val melding = meldingRepository.getReferenceById(meldingId)
            validateRightsToModifyMelding(melding, innloggetIdent)

            if (melding.notify) {
                throw IllegalOperation("Man kan ikke skru på varsel på en melding som allerede blitt varslet.")
            }

            melding.notify = true
            melding.modified = LocalDateTime.now()

            meldingRepository.save(melding)

            publishInternalEvent(
                melding = melding,
                utfoerendeIdent = innloggetIdent,
                utfoerendeName = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                timestamp = melding.modified!!,
                type = InternalEventType.MESSAGE,
            )

            val behandling = behandlingRepository.getReferenceById(behandlingId)

            if (behandling.tildeling?.saksbehandlerident != null) {
                publishNotificationEvent(
                    melding = melding,
                    utfoerendeIdent = innloggetIdent,
                    utfoerendeName = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    tildeltSaksbehandlerIdent = behandling.tildeling!!.saksbehandlerident!!,
                    behandlingType = behandling.type,
                    saksnummer = behandling.fagsakId,
                    ytelse = behandling.ytelse,
                )
            }

            logger.debug("melding ({}) modified by {}", meldingId, innloggetIdent)

            return meldingMapper.toModifiedView(melding)
        } catch (enfe: EntityNotFoundException) {
            throw MeldingNotFoundException("couldn't find melding with id $meldingId")
        }
    }

    fun getMeldingerForBehandling(behandlingId: UUID): List<MeldingView> {
        return meldingMapper.toMeldingerView(meldingRepository.findByBehandlingIdOrderByCreatedDesc(behandlingId))
    }

    private fun validateRightsToModifyMelding(melding: Melding, innloggetIdent: String) {
        if (melding.saksbehandlerident != innloggetIdent) {
            throw MissingTilgangException(
                "Saksbehandler ($innloggetIdent) is not the author of melding (${melding.id}), and is not allowed to modify it."
            )
        }
    }

    private fun validateRightsToDeleteMelding(melding: Melding, innloggetIdent: String) {
        val behandling = behandlingRepository.findById(melding.behandlingId).get()

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
        type: InternalEventType,
    ) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = melding.behandlingId.toString(),
                type = type,
                data = objectMapper.writeValueAsString(
                    MeldingEvent(
                        actor = Employee(navIdent = utfoerendeIdent, navn = utfoerendeName),
                        timestamp = timestamp,
                        id = melding.id.toString(),
                        text = melding.text,
                        notify = melding.notify,
                    )
                )
            )
        )
    }

    private fun publishNotificationEvent(
        melding: Melding,
        utfoerendeIdent: String,
        utfoerendeName: String,
        tildeltSaksbehandlerIdent: String,
        behandlingType: Type,
        saksnummer: String,
        ytelse: Ytelse,
    ) {
        kafkaInternalEventService.publishNotificationEvent(
            id = melding.id,
            jsonNode = objectMapper.valueToTree(
                CreateMeldingNotificationEvent(
                    type = CreateMeldingNotificationEvent.NotificationType.MELDING,
                    message = melding.text,
                    recipientNavIdent = tildeltSaksbehandlerIdent,
                    source = CreateMeldingNotificationEvent.NotificationSource.KABAL,
                    meldingId = melding.id,
                    behandlingId = melding.behandlingId,
                    behandlingType = behandlingType,
                    actorNavIdent = utfoerendeIdent,
                    actorNavn = utfoerendeName,
                    saksnummer = saksnummer,
                    ytelse = ytelse,
                    sourceCreatedAt = melding.modified ?: melding.created,
                )
            )
        )
    }
}

data class CreateMeldingNotificationEvent(
    val type: NotificationType,
    val message: String,
    val recipientNavIdent: String,
    val source: NotificationSource,
    val meldingId: UUID,
    val behandlingId: UUID,
    val behandlingType: Type,
    val actorNavIdent: String,
    val actorNavn: String,
    val saksnummer: String,
    val ytelse: Ytelse,
    val sourceCreatedAt: LocalDateTime,
) {
    enum class NotificationSource {
        OPPGAVE,
        KABAL,
    }

    enum class NotificationType {
        MELDING, LOST_ACCESS
    }
}