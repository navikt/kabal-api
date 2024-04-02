package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Mottak
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class CreateBehandlingFromMottak(
    private val klagebehandlingService: KlagebehandlingService,
    private val ankebehandlingService: AnkebehandlingService,
    private val kafkaEventRepository: KafkaEventRepository,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun createBehandling(mottak: Mottak): Behandling {
        logger.debug(
            "Received mottak {} in CreateBehandlingFromMottak",
            mottak.id
        )

        return when (mottak.type) {
            Type.KLAGE -> klagebehandlingService.createKlagebehandlingFromMottak(mottak)
            Type.ANKE -> {
                val ankebehandling = ankebehandlingService.createAnkebehandlingFromMottak(mottak)
                publishKafkaEvent(ankebehandling)
                ankebehandling
            }

            Type.ANKE_I_TRYGDERETTEN -> TODO()
        }
    }

    private fun publishKafkaEvent(ankebehandling: Ankebehandling) {
        //Publiser Kafka-event, infomelding om opprettelse
        val behandlingEvent = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = ankebehandling.kildeReferanse,
            kilde = ankebehandling.fagsystem.navn,
            kabalReferanse = ankebehandling.id.toString(),
            type = BehandlingEventType.ANKEBEHANDLING_OPPRETTET,
            detaljer = BehandlingDetaljer(
                ankebehandlingOpprettet =
                AnkebehandlingOpprettetDetaljer(
                    mottattKlageinstans = ankebehandling.created
                )
            )
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = ankebehandling.id,
                kilde = ankebehandling.fagsystem.navn,
                kildeReferanse = ankebehandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )
    }
}