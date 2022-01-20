package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class StatistikkTilDVHService(
    private val mottakRepository: MottakRepository,
    private val kafkaEventRepository: KafkaEventRepository
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    }

    fun process(behandlingEndretEvent: BehandlingEndretEvent) {
        if (shouldSendStats(behandlingEndretEvent.endringslogginnslag)) {

            //FIXME handle anke

            val behandling = behandlingEndretEvent.behandling
            if (behandling.type == Type.KLAGE) {
                val eventId = UUID.randomUUID()

                val klageStatistikkTilDVH = createKlageStatistikkTilDVH(
                    eventId = eventId,
                    klagebehandling = behandling as Klagebehandling,
                    behandlingState = getKlagebehandlingState(behandlingEndretEvent.endringslogginnslag)
                )

                kafkaEventRepository.save(
                    KafkaEvent(
                        id = eventId,
                        klagebehandlingId = behandlingEndretEvent.behandling.id,
                        kilde = behandlingEndretEvent.behandling.kildesystem.navn,
                        kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                        status = UtsendingStatus.IKKE_SENDT,
                        jsonPayload = klageStatistikkTilDVH.toJson(),
                        type = EventType.STATS_DVH
                    )
                )
            }

        }
    }

    private fun KlageStatistikkTilDVH.toJson(): String = objectMapper.writeValueAsString(this)

    private fun shouldSendStats(endringslogginnslag: List<Endringslogginnslag>) =
        endringslogginnslag.isEmpty() ||
                endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT || it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER }

    private fun getKlagebehandlingState(endringslogginnslag: List<Endringslogginnslag>): BehandlingState {
        return when {
            endringslogginnslag.isEmpty() -> BehandlingState.MOTTATT
            endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT } -> BehandlingState.TILDELT_SAKSBEHANDLER
            endringslogginnslag.any { it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER } -> BehandlingState.AVSLUTTET
            else -> BehandlingState.UKJENT.also {
                logger.warn(
                    "unknown state for klagebehandling with id {}",
                    endringslogginnslag.first().behandlingId
                )
            }
        }
    }

    private fun createKlageStatistikkTilDVH(
        eventId: UUID,
        klagebehandling: Klagebehandling,
        behandlingState: BehandlingState
    ): KlageStatistikkTilDVH {

        val funksjoneltEndringstidspunkt =
            getFunksjoneltEndringstidspunkt(klagebehandling, behandlingState)

        return KlageStatistikkTilDVH(
            eventId = eventId,
            behandlingId = klagebehandling.dvhReferanse ?: klagebehandling.kildeReferanse,
            behandlingIdKabal = klagebehandling.id.toString(),
            behandlingStartetKA = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            behandlingStatus = behandlingState,
            behandlingType = klagebehandling.type.navn,
            beslutter = klagebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident,
            endringstid = funksjoneltEndringstidspunkt,
            hjemmel = klagebehandling.hjemler.map { it.toSearchableString() },
            klager = getPart(klagebehandling.klager.partId.type, klagebehandling.klager.partId.value),
            opprinneligFagsaksystem = klagebehandling.kildesystem.navn,
            overfoertKA = klagebehandling.mottattKlageinstans.toLocalDate(),
            resultat = klagebehandling.currentDelbehandling().utfall?.name?.let { ExternalUtfall.valueOf(it).navn },
            sakenGjelder = getPart(klagebehandling.sakenGjelder.partId.type, klagebehandling.sakenGjelder.partId.value),
            saksbehandler = klagebehandling.tildeling?.saksbehandlerident,
            saksbehandlerEnhet = klagebehandling.tildeling?.enhet,
            tekniskTid = klagebehandling.modified,
            vedtakId = klagebehandling.currentDelbehandling().id.toString(),
            vedtaksdato = klagebehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            ytelseType = "TODO",
        )
    }

    private fun getFunksjoneltEndringstidspunkt(
        klagebehandling: Klagebehandling,
        behandlingState: BehandlingState
    ): LocalDateTime {
        return when (behandlingState) {
            BehandlingState.MOTTATT -> klagebehandling.mottattKlageinstans
            BehandlingState.TILDELT_SAKSBEHANDLER -> klagebehandling.tildeling?.tidspunkt
                ?: throw RuntimeException("tildelt mangler")
            BehandlingState.AVSLUTTET -> klagebehandling.avsluttetAvSaksbehandler
                ?: throw RuntimeException("avsluttetAvSaksbehandler mangler")
            BehandlingState.UKJENT -> {
                logger.warn("Unknown funksjoneltEndringstidspunkt. Missing state.")
                LocalDateTime.now()
            }
        }
    }

    private fun getPart(type: PartIdType, value: String) =
        when (type) {
            PartIdType.PERSON -> {
                KlageStatistikkTilDVH.Part(
                    verdi = value,
                    type = KlageStatistikkTilDVH.PartIdType.PERSON
                )
            }
            PartIdType.VIRKSOMHET -> {
                KlageStatistikkTilDVH.Part(
                    verdi = value,
                    type = KlageStatistikkTilDVH.PartIdType.VIRKSOMHET
                )
            }
        }
}