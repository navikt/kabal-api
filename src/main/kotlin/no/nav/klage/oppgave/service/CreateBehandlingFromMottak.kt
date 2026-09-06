package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.kafka.AnkebehandlingOpprettetDetaljer
import no.nav.klage.oppgave.domain.kafka.BehandlingDetaljer
import no.nav.klage.oppgave.domain.kafka.BehandlingEvent
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

@Service
class CreateBehandlingFromMottak(
    private val klagebehandlingService: KlagebehandlingService,
    private val ankebehandlingService: AnkebehandlingService,
    private val omgjoeringskravbehandlingService: OmgjoeringskravbehandlingService,
    private val gjenopptaksbehandlingService: GjenopptaksbehandlingService,
    private val kafkaEventRepository: KafkaEventRepository,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapperBehandlingEvents = jacksonObjectMapper()
    }

    fun createBehandling(mottak: Mottak): Behandling {
        logger.debug("Received mottak in CreateBehandlingFromMottak")

        return when (mottak.type) {
            Type.KLAGE -> {
                klagebehandlingService.createKlagebehandlingFromMottak(
                    mottak = mottak,
                )
            }

            Type.ANKE_FOER_2027 -> {
                val ankebehandling =
                    ankebehandlingService.createAnkebehandlingFromMottak(
                        mottak = mottak,
                    )

                if (!ankebehandling.gosysOppgaveRequired) {
                    publishKafkaEvent(ankebehandling)
                }

                ankebehandling
            }

            Type.OMGJOERINGSKRAV -> {
                omgjoeringskravbehandlingService.createOmgjoeringskravbehandlingFromMottak(
                    mottak = mottak,
                )
            }

            Type.ANKE_I_TRYGDERETTEN_FOER_2027 -> {
                TODO()
            }

            Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> {
                TODO()
            }

            Type.BEGJAERING_OM_GJENOPPTAK -> {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromMottak(
                    mottak = mottak,
                )
            }

            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN -> {
                TODO()
            }

            Type.ANKE_I_TRYGDERETTEN_ETTER_2027, Type.ANKE_ETTER_2027 -> {
                TODO()
            }
        }
    }

    private fun publishKafkaEvent(ankebehandling: AnkebehandlingFoer2027) {
        // Publiser Kafka-event, infomelding om opprettelse
        val behandlingEvent =
            BehandlingEvent(
                eventId = UUID.randomUUID(),
                kildeReferanse = ankebehandling.kildeReferanse,
                kilde = ankebehandling.fagsystem.navn,
                kabalReferanse = ankebehandling.id.toString(),
                type = BehandlingEventType.ANKEBEHANDLING_OPPRETTET,
                detaljer =
                    BehandlingDetaljer(
                        ankebehandlingOpprettet =
                            AnkebehandlingOpprettetDetaljer(
                                mottattKlageinstans = ankebehandling.mottattKlageinstans,
                            ),
                    ),
            )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = ankebehandling.id,
                kilde = ankebehandling.fagsystem.navn,
                kildeReferanse = ankebehandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT,
            ),
        )
    }
}
