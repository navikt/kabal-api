package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
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
        return if (behandlingEndretEvent.behandling.shouldUpdateInfotrygd() || behandlingEndretEvent.behandling is OmgjoeringskravbehandlingBasedOnJournalpost) {
            false
        } else behandlingEndretEvent.endringslogginnslag.any {
            it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT
                    || it.felt === Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT
                    || it.felt === Felt.FEILREGISTRERING
                    || it.felt === Felt.KLAGEBEHANDLING_MOTTATT
                    || it.felt === Felt.ANKEBEHANDLING_MOTTATT
                    || it.felt === Felt.OMGJOERINGSKRAVBEHANDLING_MOTTATT
                    || it.felt === Felt.KLAGEBEHANDLING_OPPRETTET
                    || it.felt === Felt.ANKEBEHANDLING_OPPRETTET
                    || it.felt === Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET
        }
    }

    private fun getBehandlingState(behandlingEndretEvent: BehandlingEndretEvent): BehandlingState {
        val endringslogginnslag: List<Endringslogginnslag> = behandlingEndretEvent.endringslogginnslag
        val behandling = behandlingEndretEvent.behandling
        val type = behandling.type
        val utfall = behandling.utfall

        return when {
            endringslogginnslag.any {
                it.felt === Felt.KLAGEBEHANDLING_MOTTATT ||
                        it.felt === Felt.ANKEBEHANDLING_MOTTATT ||
                        it.felt === Felt.OMGJOERINGSKRAVBEHANDLING_MOTTATT
            } -> BehandlingState.MOTTATT

            endringslogginnslag.any {
                it.felt === Felt.KLAGEBEHANDLING_OPPRETTET ||
                        it.felt === Felt.ANKEBEHANDLING_OPPRETTET ||
                        it.felt === Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET
            } -> BehandlingState.OPPRETTET

            endringslogginnslag.any {
                it.felt === Felt.FEILREGISTRERING
            } -> BehandlingState.AVSLUTTET

            endringslogginnslag.any {
                it.felt === Felt.NY_BEHANDLING_ETTER_TR_OPPHEVET
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
        val behandlingId = if (behandling.fagsystem == Fagsystem.IT01) {
            try {
                behandling.fagsakId.substring(0, 4) + "-" + behandling.fagsakId.substring(
                    4,
                    5
                ) + "-" + behandling.fagsakId.substring(5)
            } catch (e: Exception) {
                logger.error(
                    "Error while generating Infotrygd behandlingId, for DVH, for behandling with id ${behandling.id} and fagsakId ${behandling.fagsakId}",
                    e
                )
                behandling.fagsakId
            }
        } else {
            behandling.dvhReferanse ?: behandling.kildeReferanse
        }

        return StatistikkTilDVH(
            eventId = eventId,
            behandlingId = behandlingId,
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
            klager = getDVHPart(behandling.klager.partId.type, behandling.klager.partId.value),
            opprinneligFagsaksystem = behandling.fagsystem.navn,
            overfoertKA = behandling.mottattKlageinstans.toLocalDate(),
            resultat = getResultat(behandling),
            sakenGjelder = getDVHPart(behandling.sakenGjelder.partId.type, behandling.sakenGjelder.partId.value),
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
            Type.ANKE.name
        } else {
            type.name
        }

    private fun getResultat(behandling: Behandling): String? =
        if (behandling.feilregistrering != null) {
            "Feilregistrert"
        } else if (behandling.ferdigstilling != null) {
            behandling.utfall?.name
        } else {
            null
        }

    private fun getFunksjoneltEndringstidspunkt(
        behandling: Behandling,
        behandlingState: BehandlingState
    ): LocalDateTime {
        return when (behandlingState) {
            BehandlingState.MOTTATT -> behandling.mottattKlageinstans

            BehandlingState.OPPRETTET -> behandling.created

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
                    if (behandling.utfall != null) {
                        behandling.kjennelseMottatt ?: throw RuntimeException("kjennelseMottatt mangler")
                    } else {
                        behandling.nyAnkebehandlingKA ?: throw RuntimeException("nyAnkebehandlingKA mangler")
                    }
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

            else -> {
                error("BehandlingState not in use. ${behandlingState.name}")
            }
        }
    }

    private fun Registreringshjemmel.toSearchableString(): String {
        return "${lovKilde.navn}-${spesifikasjon}"
    }
}

fun getDVHPart(type: PartIdType, value: String) =
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