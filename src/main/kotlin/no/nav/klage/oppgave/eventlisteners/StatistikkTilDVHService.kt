package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.oppgave.domain.events.KlagebehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
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

    fun process(klagebehandlingEndretEvent: KlagebehandlingEndretEvent) {
        if (shouldSendStats(klagebehandlingEndretEvent.endringslogginnslag)) {

            val klagebehandling = klagebehandlingEndretEvent.klagebehandling
            val eventId = UUID.randomUUID()

            val klageStatistikkTilDVH = createKlageStatistikkTilDVH(
                eventId = eventId,
                klagebehandling = klagebehandling,
                klagebehandlingState = getKlagebehandlingState(klagebehandlingEndretEvent.endringslogginnslag)
            )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    klagebehandlingId = klagebehandlingEndretEvent.klagebehandling.id,
                    kilde = klagebehandlingEndretEvent.klagebehandling.kildesystem.navn,
                    kildeReferanse = klagebehandlingEndretEvent.klagebehandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = klageStatistikkTilDVH.toJson(),
                    type = EventType.STATS_DVH
                )
            )
        }
    }

    private fun KlageStatistikkTilDVH.toJson(): String = objectMapper.writeValueAsString(this)

    private fun shouldSendStats(endringslogginnslag: List<Endringslogginnslag>) =
        endringslogginnslag.isEmpty() ||
                endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT || it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER }

    private fun getKlagebehandlingState(endringslogginnslag: List<Endringslogginnslag>): KlagebehandlingState {
        return when {
            endringslogginnslag.isEmpty() -> KlagebehandlingState.MOTTATT
            endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT } -> KlagebehandlingState.TILDELT_SAKSBEHANDLER
            endringslogginnslag.any { it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER } -> KlagebehandlingState.AVSLUTTET
            else -> KlagebehandlingState.UKJENT.also {
                logger.warn(
                    "unknown state for klagebehandling with id {}",
                    endringslogginnslag.first().klagebehandlingId
                )
            }
        }
    }

    private fun createKlageStatistikkTilDVH(
        eventId: UUID,
        klagebehandling: Klagebehandling,
        klagebehandlingState: KlagebehandlingState
    ): KlageStatistikkTilDVH {
        val vedtak = klagebehandling.vedtak

        val funksjoneltEndringstidspunkt =
            getFunksjoneltEndringstidspunkt(klagebehandling, klagebehandlingState)

        return KlageStatistikkTilDVH(
            eventId = eventId,
            behandlingId = klagebehandling.dvhReferanse ?: klagebehandling.kildeReferanse,
            behandlingIdKabal = klagebehandling.id.toString(),
            behandlingStartetKA = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            behandlingStatus = klagebehandlingState,
            behandlingType = klagebehandling.type.navn,
            beslutter = klagebehandling.medunderskriver?.saksbehandlerident,
            endringstid = funksjoneltEndringstidspunkt,
            hjemmel = klagebehandling.hjemler.map { it.toSearchableString() },
            klager = getPart(klagebehandling.klager.partId.type, klagebehandling.klager.partId.value),
            omgjoeringsgrunn = vedtak.grunn?.navn,
            opprinneligFagsaksystem = klagebehandling.kildesystem.navn,
            overfoertKA = klagebehandling.created.toLocalDate(),
            resultat = vedtak.utfall?.navn,
            sakenGjelder = getPart(klagebehandling.sakenGjelder.partId.type, klagebehandling.sakenGjelder.partId.value),
            saksbehandler = klagebehandling.tildeling?.saksbehandlerident,
            saksbehandlerEnhet = klagebehandling.tildeling?.enhet,
            tekniskTid = klagebehandling.modified,
            vedtakId = vedtak.id.toString(),
            vedtaksdato = klagebehandling.avsluttetAvSaksbehandler?.toLocalDate(),
            ytelseType = "TODO",
        )
    }

    private fun getFunksjoneltEndringstidspunkt(
        klagebehandling: Klagebehandling,
        klagebehandlingState: KlagebehandlingState
    ): LocalDateTime {
        return when (klagebehandlingState) {
            KlagebehandlingState.MOTTATT -> klagebehandling.mottattKlageinstans
            KlagebehandlingState.TILDELT_SAKSBEHANDLER -> klagebehandling.tildeling?.tidspunkt
                ?: throw RuntimeException("tildelt mangler")
            KlagebehandlingState.AVSLUTTET -> klagebehandling.avsluttetAvSaksbehandler
                ?: throw RuntimeException("avsluttetAvSaksbehandler mangler")
            KlagebehandlingState.UKJENT -> {
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