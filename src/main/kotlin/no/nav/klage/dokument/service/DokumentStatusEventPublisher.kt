package no.nav.klage.dokument.service

import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KafkaInternalEventService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.*

/**
 * Tells everyone looking at the behandling how far an uploaded document has come, so that they see
 * the same thing as the user that uploaded it.
 */
@Service
class DokumentStatusEventPublisher(
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapper = jacksonObjectMapper()
    }

    fun publish(behandlingId: UUID, dokumentId: UUID, state: DokumentState) {
        try {
            val navIdent = innloggetSaksbehandlerService.getInnloggetIdent()

            kafkaInternalEventService.publishInternalBehandlingEvent(
                InternalBehandlingEvent(
                    behandlingId = behandlingId.toString(),
                    type = InternalEventType.DOCUMENT_STATUS_CHANGED,
                    data = objectMapper.writeValueAsString(
                        DocumentStatusChangedEvent(
                            actor = Employee(
                                navIdent = navIdent,
                                navn = saksbehandlerService.getNameForIdentDefaultIfNull(navIdent),
                            ),
                            timestamp = LocalDateTime.now(),
                            document = DocumentStatusChangedEvent.DocumentStatusChanged(
                                id = dokumentId.toString(),
                                parentId = state.parentId?.toString(),
                                status = state.status,
                                size = state.size,
                            ),
                            traceparent = currentTraceparent(),
                        )
                    ),
                )
            )
        } catch (e: Exception) {
            //Best effort: the user that uploaded the document gets the status through the confirm
            //stream regardless.
            logger.warn("Could not publish status event for document $dokumentId", e)
        }
    }
}
