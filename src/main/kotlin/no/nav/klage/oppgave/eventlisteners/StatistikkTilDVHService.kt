package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class StatistikkTilDVHService(
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
            val behandling = behandlingEndretEvent.behandling

            val eventId = UUID.randomUUID()
            val statistikkTilDVH = createStatistikkTilDVH(
                eventId = eventId,
                behandling = behandling,
                behandlingState = getBehandlingState(behandlingEndretEvent.endringslogginnslag)
            )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    behandlingId = behandlingEndretEvent.behandling.id,
                    kilde = behandlingEndretEvent.behandling.sakFagsystem.navn,
                    kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = statistikkTilDVH.toJson(),
                    type = EventType.STATS_DVH
                )
            )
        }
    }

    private fun StatistikkTilDVH.toJson(): String = objectMapper.writeValueAsString(this)

    private fun shouldSendStats(endringslogginnslag: List<Endringslogginnslag>) =
        endringslogginnslag.isEmpty() ||
                endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT || it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER }

    private fun getBehandlingState(endringslogginnslag: List<Endringslogginnslag>): BehandlingState {
        return when {
            endringslogginnslag.isEmpty() -> BehandlingState.MOTTATT
            endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT } -> BehandlingState.TILDELT_SAKSBEHANDLER
            endringslogginnslag.any { it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER } -> BehandlingState.AVSLUTTET
            else -> BehandlingState.UKJENT.also {
                logger.warn(
                    "unknown state for behandling with id {}",
                    endringslogginnslag.first().behandlingId
                )
            }
        }
    }

    private fun createStatistikkTilDVH(
        eventId: UUID,
        behandling: Behandling,
        behandlingState: BehandlingState
    ): StatistikkTilDVH {
        val funksjoneltEndringstidspunkt =
            getFunksjoneltEndringstidspunkt(behandling, behandlingState)

        val resultat = getResultat(behandling)

        return StatistikkTilDVH(
            eventId = eventId,
            behandlingId = behandling.dvhReferanse ?: behandling.kildeReferanse,
            behandlingIdKabal = behandling.id.toString(),
            behandlingStartetKA = behandling.tildeling?.tidspunkt?.toLocalDate(),
            behandlingStatus = behandlingState,
            behandlingType = behandling.type.navn,
            beslutter = behandling.currentDelbehandling().medunderskriver?.saksbehandlerident,
            endringstid = funksjoneltEndringstidspunkt,
            hjemmel = behandling.hjemler.map { it.toSearchableString() },
            klager = getPart(behandling.klager.partId.type, behandling.klager.partId.value),
            opprinneligFagsaksystem = behandling.sakFagsystem.navn,
            overfoertKA = behandling.mottattKlageinstans.toLocalDate(),
            resultat = resultat,
            sakenGjelder = getPart(behandling.sakenGjelder.partId.type, behandling.sakenGjelder.partId.value),
            saksbehandler = behandling.tildeling?.saksbehandlerident,
            saksbehandlerEnhet = behandling.tildeling?.enhet,
            tekniskTid = behandling.modified,
            vedtakId = behandling.currentDelbehandling().id.toString(),
            vedtaksdato = behandling.currentDelbehandling().avsluttetAvSaksbehandler?.toLocalDate(),
            ytelseType = "TODO",
        )
    }

    //Resultat should only be relevant if avsluttetAvSaksbehandler
    private fun getResultat(behandling: Behandling): String? =
        if (behandling.avsluttetAvSaksbehandler != null) {
            behandling.currentDelbehandling().utfall?.name?.let { ExternalUtfall.valueOf(it).navn }
        } else {
            null
        }

    private fun getFunksjoneltEndringstidspunkt(
        behandling: Behandling,
        behandlingState: BehandlingState
    ): LocalDateTime {
        return when (behandlingState) {
            BehandlingState.MOTTATT -> behandling.mottattKlageinstans
            BehandlingState.TILDELT_SAKSBEHANDLER -> behandling.tildeling?.tidspunkt
                ?: throw RuntimeException("tildelt mangler")
            BehandlingState.AVSLUTTET -> behandling.currentDelbehandling().avsluttetAvSaksbehandler
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
                StatistikkTilDVH.Part(
                    verdi = value,
                    type = StatistikkTilDVH.PartIdType.PERSON
                )
            }
            PartIdType.VIRKSOMHET -> {
                StatistikkTilDVH.Part(
                    verdi = value,
                    type = StatistikkTilDVH.PartIdType.VIRKSOMHET
                )
            }
        }
}