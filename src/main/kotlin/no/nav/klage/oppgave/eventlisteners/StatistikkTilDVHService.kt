package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
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
        private val objectMapper = ourJacksonObjectMapper()

        const val TR_ENHET = "TR0000"
    }

    fun process(behandlingEndretEvent: BehandlingEndretEvent) {
        if (shouldSendStats(behandlingEndretEvent)) {
            val behandling = behandlingEndretEvent.behandling

            val eventId = UUID.randomUUID()
            val statistikkTilDVH = createStatistikkTilDVH(
                eventId = eventId,
                behandling = behandling,
                behandlingState = getBehandlingState(behandlingEndretEvent)
            )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    behandlingId = behandlingEndretEvent.behandling.id,
                    kilde = behandlingEndretEvent.behandling.fagsystem.navn,
                    kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = statistikkTilDVH.toJson(),
                    type = EventType.STATS_DVH
                )
            )
        }
    }

    private fun StatistikkTilDVH.toJson(): String = objectMapper.writeValueAsString(this)

    fun shouldSendStats(behandlingEndretEvent: BehandlingEndretEvent): Boolean {
        return if (behandlingEndretEvent.behandling.fagsystem == Fagsystem.IT01) {
            false
        } else if (behandlingEndretEvent.endringslogginnslag.isEmpty() && behandlingEndretEvent.behandling.type != Type.ANKE_I_TRYGDERETTEN) {
            true
        } else behandlingEndretEvent.endringslogginnslag.any {
            it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT
                    || it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                    || it.felt === Felt.FEILREGISTRERING
        }
    }

    private fun getBehandlingState(behandlingEndretEvent: BehandlingEndretEvent): BehandlingState {
        val endringslogginnslag: List<Endringslogginnslag> = behandlingEndretEvent.endringslogginnslag
        val behandling = behandlingEndretEvent.behandling
        val type = behandling.type
        val utfall = behandling.utfall

        return when {
            endringslogginnslag.isEmpty() && type != Type.ANKE_I_TRYGDERETTEN -> BehandlingState.MOTTATT

            endringslogginnslag.any {
                it.felt === Felt.FEILREGISTRERING
            } -> BehandlingState.AVSLUTTET

            endringslogginnslag.any {
                it.felt === Felt.BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET
                        && type == Type.ANKE_I_TRYGDERETTEN
            } -> BehandlingState.AVSLUTTET_I_TR_MED_OPPHEVET_OG_NY_BEHANDLING_I_KA

            endringslogginnslag.any {
                it.felt === Felt.NY_ANKEBEHANDLING_KA
                        && type == Type.ANKE_I_TRYGDERETTEN
            } -> BehandlingState.NY_ANKEBEHANDLING_I_KA_UTEN_TR

            endringslogginnslag.any {
                it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                        && type == Type.ANKE_I_TRYGDERETTEN
                        && utfall in utfallToNewAnkebehandling
            } -> BehandlingState.AVSLUTTET_I_TR_OG_NY_ANKEBEHANDLING_I_KA

            endringslogginnslag.any {
                it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                        && type == Type.ANKE_I_TRYGDERETTEN
            } -> BehandlingState.AVSLUTTET

            endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT } -> BehandlingState.TILDELT_SAKSBEHANDLER

            endringslogginnslag.any {
                it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                        && type == Type.ANKE
                        && utfall !in utfallToTrygderetten
            } -> BehandlingState.AVSLUTTET

            endringslogginnslag.any {
                it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                        && type != Type.ANKE
            } -> BehandlingState.AVSLUTTET

            endringslogginnslag.any {
                it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                        && type == Type.ANKE
                        && utfall in utfallToTrygderetten
            } -> BehandlingState.SENDT_TIL_TR

            else -> BehandlingState.UKJENT.also {
                logger.warn(
                    "unknown state for behandling with id {}",
                    endringslogginnslag.first().behandlingId
                )
            }
        }
    }

    private fun getEnhetInCaseOfTR(behandling: Behandling, behandlingState: BehandlingState): String? {
        return if (behandling.type == Type.ANKE_I_TRYGDERETTEN && behandlingState in listOf(
                BehandlingState.AVSLUTTET,
                BehandlingState.AVSLUTTET_I_TR_OG_NY_ANKEBEHANDLING_I_KA,
            )
        ) {
            behandling as AnkeITrygderettenbehandling
            if (behandling.nyAnkebehandlingKA != null) {
                behandling.tildeling!!.enhet
            } else {
                TR_ENHET
            }
        } else {
            null
        }
    }

    private fun createStatistikkTilDVH(
        eventId: UUID,
        behandling: Behandling,
        behandlingState: BehandlingState
    ): StatistikkTilDVH {
        return StatistikkTilDVH(
            eventId = eventId,
            behandlingId = behandling.dvhReferanse ?: behandling.kildeReferanse,
            behandlingIdKabal = behandling.id.toString(),
            //Means enhetTildeltDato
            behandlingStartetKA = behandling.tildeling?.tidspunkt?.toLocalDate(),
            ansvarligEnhetKode = getEnhetInCaseOfTR(behandling, behandlingState),
            behandlingStatus = behandlingState,
            behandlingType = getBehandlingTypeName(behandling.type),
            //Means medunderskriver
            beslutter = behandling.medunderskriver?.saksbehandlerident,
            endringstid = getFunksjoneltEndringstidspunkt(behandling, behandlingState),
            hjemmel = behandling.registreringshjemler.map { it.toSearchableString() },
            klager = getPart(behandling.klager.partId.type, behandling.klager.partId.value),
            opprinneligFagsaksystem = behandling.fagsystem.navn,
            overfoertKA = behandling.mottattKlageinstans.toLocalDate(),
            resultat = getResultat(behandling),
            sakenGjelder = getPart(behandling.sakenGjelder.partId.type, behandling.sakenGjelder.partId.value),
            saksbehandler = behandling.tildeling?.saksbehandlerident,
            saksbehandlerEnhet = behandling.tildeling?.enhet,
            tekniskTid = behandling.modified,
            vedtaksdato = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            ytelseType = behandling.ytelse.name,
            opprinneligFagsakId = behandling.fagsakId,
        )
    }

    private fun getBehandlingTypeName(type: Type): String =
        if (type == Type.ANKE_I_TRYGDERETTEN) {
            Type.ANKE.navn
        } else {
            type.navn
        }

    private fun getResultat(behandling: Behandling): String? =
        if (behandling.feilregistrering != null) {
            "Feilregistrert"
        } else if (behandling.ferdigstilling != null) {
            behandling.utfall?.navn
        } else {
            null
        }

    private fun getFunksjoneltEndringstidspunkt(
        behandling: Behandling,
        behandlingState: BehandlingState
    ): LocalDateTime {
        return when (behandlingState) {
            BehandlingState.MOTTATT -> behandling.mottattKlageinstans
            BehandlingState.TILDELT_SAKSBEHANDLER -> behandling.modified //tildelt eller fradelt

            BehandlingState.AVSLUTTET_I_TR_OG_NY_ANKEBEHANDLING_I_KA -> {
                behandling as AnkeITrygderettenbehandling
                behandling.kjennelseMottatt ?: throw RuntimeException("kjennelseMottatt mangler")
            }

            BehandlingState.NY_ANKEBEHANDLING_I_KA_UTEN_TR -> {
                behandling.ferdigstilling?.avsluttetAvSaksbehandler
                    ?: throw RuntimeException("avsluttetAvSaksbehandler mangler")
            }

            BehandlingState.AVSLUTTET -> {
                if (behandling.feilregistrering != null) {
                    behandling.feilregistrering!!.registered
                } else if (behandling is AnkeITrygderettenbehandling) {
                    behandling.kjennelseMottatt ?: throw RuntimeException("kjennelseMottatt mangler")
                } else {
                    behandling.ferdigstilling?.avsluttetAvSaksbehandler
                        ?: throw RuntimeException("avsluttetAvSaksbehandler mangler")
                }
            }

            BehandlingState.UKJENT -> {
                logger.warn("Unknown funksjoneltEndringstidspunkt. Missing state.")
                LocalDateTime.now()
            }

            BehandlingState.SENDT_TIL_TR -> behandling.ferdigstilling?.avsluttetAvSaksbehandler
                ?: throw RuntimeException("avsluttetAvSaksbehandler mangler")

            BehandlingState.AVSLUTTET_I_TR_MED_OPPHEVET_OG_NY_BEHANDLING_I_KA -> {
                behandling as AnkeITrygderettenbehandling
                behandling.kjennelseMottatt ?: throw RuntimeException("kjennelseMottatt mangler")
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

    private fun Registreringshjemmel.toSearchableString(): String {
        return "${lovKilde.navn}-${spesifikasjon}"
    }
}