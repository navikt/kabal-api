package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingITrygderetten
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Felt
import no.nav.klage.oppgave.domain.kafka.BehandlingStateV2
import no.nav.klage.oppgave.domain.kafka.BehandlingTypeV2
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVHV2
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class StatistikkTilDVHV2Service(
    private val kafkaEventRepository: KafkaEventRepository,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()

        const val TR_ENHET = "TR0000"

        // *_MOTTATT is published together with *_OPPRETTET, so only *_OPPRETTET is used to avoid duplicates.
        private val opprettetFelter =
            setOf(
                Felt.KLAGEBEHANDLING_OPPRETTET,
                Felt.ANKEBEHANDLING_OPPRETTET,
                Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET,
                Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET,
                Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                Felt.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_OPPRETTET,
                Felt.BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
                Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN,
                Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET_BASERT_PAA_BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
            )

        private val avsluttetFelter =
            setOf(
                Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                Felt.FEILREGISTRERING,
            )
    }

    fun process(behandlingChangedEvent: BehandlingChangedEvent) {
        val behandling = behandlingChangedEvent.behandling
        if (behandling.gosysOppgaveRequired) {
            return
        }

        val behandlingState = getBehandlingState(behandlingChangedEvent.changeList) ?: return
        val now = LocalDateTime.now()

        saveEvent(
            behandling = behandling,
            statistikk =
                createStatistikkTilDVHV2(
                    behandling = behandling,
                    behandlingState = behandlingState,
                    tekniskTid = now,
                ),
            created = now,
        )

        // Behandlinger created can be already assigned.
        if (behandlingState == BehandlingStateV2.OPPRETTET && behandling.tildeling?.saksbehandlerident != null) {
            saveEvent(
                behandling = behandling,
                statistikk =
                    createStatistikkTilDVHV2(
                        behandling = behandling,
                        behandlingState = BehandlingStateV2.TILDELT_SAKSBEHANDLER,
                        tekniskTid = now,
                    ),
                created = now.plusNanos(1_000),
            )
        }
    }

    fun getBehandlingState(changeList: List<BehandlingChangedEvent.Change>): BehandlingStateV2? {
        val felter = changeList.map { it.felt }.toSet()
        return when {
            felter.any { it in opprettetFelter } -> {
                BehandlingStateV2.OPPRETTET
            }

            felter.any { it in avsluttetFelter } -> {
                BehandlingStateV2.AVSLUTTET
            }

            Felt.TILDELT_SAKSBEHANDLERIDENT in felter -> {
                BehandlingStateV2.TILDELT_SAKSBEHANDLER
            }

            else -> {
                logger.debug("BehandlingChangedEvent does not contain any relevant fields for DVH v2. ChangeList: {}", changeList)
                null
            }
        }
    }

    private fun saveEvent(
        behandling: Behandling,
        statistikk: StatistikkTilDVHV2,
        created: LocalDateTime,
    ) {
        kafkaEventRepository.save(
            KafkaEvent(
                id = statistikk.eventId,
                behandlingId = behandling.id,
                kilde = behandling.fagsystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                status = UtsendingStatus.IKKE_SENDT,
                jsonPayload = jacksonObjectMapper.writeValueAsString(statistikk),
                created = created,
                type = EventType.STATS_DVH_V2,
            ),
        )
    }

    private fun createStatistikkTilDVHV2(
        behandling: Behandling,
        behandlingState: BehandlingStateV2,
        tekniskTid: LocalDateTime,
    ): StatistikkTilDVHV2 {
        val tildeling = if (behandlingState == BehandlingStateV2.OPPRETTET) null else behandling.tildeling

        return StatistikkTilDVHV2(
            eventId = UUID.randomUUID(),
            ansvarligEnhetKode =
                getAnsvarligEnhetKode(
                    behandling = behandling,
                    behandlingState = behandlingState,
                    saksbehandlerEnhet = tildeling?.enhet,
                ),
            behandlingId = getDVHBehandlingId(behandling),
            behandlingIdKabal = behandling.id.toString(),
            behandlingStartetKA =
                if (behandlingState == BehandlingStateV2.OPPRETTET) null else getFoersteTildelingsdato(behandling),
            behandlingStatus = behandlingState,
            behandlingType = getBehandlingType(behandling.type),
            beslutter = behandling.medunderskriver?.saksbehandlerident,
            endringstid = getEndringstid(behandling = behandling, behandlingState = behandlingState),
            hjemmel = behandling.registreringshjemler.map { it.toSearchableString() },
            klager = getPart(type = behandling.klager.partId.type, value = behandling.klager.partId.value),
            opprinneligFagsaksystem = behandling.fagsystem.navn,
            opprinneligFagsakId = behandling.fagsakId,
            overfoertKA = behandling.mottattKlageinstans.toLocalDate(),
            resultat = getResultat(behandling),
            sakenGjelder = getPart(type = behandling.sakenGjelder.partId.type, value = behandling.sakenGjelder.partId.value),
            saksbehandler = tildeling?.saksbehandlerident,
            saksbehandlerEnhet = tildeling?.enhet,
            tekniskTid = tekniskTid,
            vedtaksdato = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            ytelseType = behandling.ytelse.name,
        )
    }

    private fun getAnsvarligEnhetKode(
        behandling: Behandling,
        behandlingState: BehandlingStateV2,
        saksbehandlerEnhet: String?,
    ): String? =
        if (behandlingState == BehandlingStateV2.AVSLUTTET && isAvsluttetAvTrygderetten(behandling)) {
            TR_ENHET
        } else {
            saksbehandlerEnhet
        }

    private fun isAvsluttetAvTrygderetten(behandling: Behandling): Boolean {
        if (behandling !is BehandlingITrygderetten || behandling.feilregistrering != null) {
            return false
        }
        val nyBehandlingIKAUtenTR =
            when (behandling) {
                is AnkeITrygderettenbehandling -> behandling.nyAnkebehandlingKA != null
                is GjenopptakITrygderettenbehandling -> behandling.nyGjenopptaksbehandlingKA != null
                else -> false
            }
        return !nyBehandlingIKAUtenTR
    }

    private fun getFoersteTildelingsdato(behandling: Behandling): LocalDate? =
        (
            behandling.tildelingHistorikk
                .filter { it.saksbehandlerident != null }
                .map { it.tidspunkt } +
                listOfNotNull(behandling.tildeling?.takeIf { it.saksbehandlerident != null }?.tidspunkt)
        ).minOrNull()?.toLocalDate()

    private fun getEndringstid(
        behandling: Behandling,
        behandlingState: BehandlingStateV2,
    ): LocalDateTime =
        when (behandlingState) {
            BehandlingStateV2.OPPRETTET -> {
                behandling.created
            }

            BehandlingStateV2.TILDELT_SAKSBEHANDLER -> {
                behandling.modified
            }

            BehandlingStateV2.AVSLUTTET -> {
                getAvsluttetTidspunkt(behandling)
            }
        }

    private fun getAvsluttetTidspunkt(behandling: Behandling): LocalDateTime {
        val feilregistrering = behandling.feilregistrering
        val tidspunkt =
            when {
                feilregistrering != null -> {
                    feilregistrering.registered
                }

                behandling is BehandlingITrygderetten && behandling.utfall != null -> {
                    behandling.kjennelseMottatt
                }

                else -> {
                    behandling.ferdigstilling?.avsluttetAvSaksbehandler
                }
            }

        // Never block saksbehandling because of statistics. Fall back to modified, and log so it can be corrected.
        return tidspunkt ?: behandling.modified.also {
            logger.error("Missing avsluttet-tidspunkt for DVH v2 for behandling {}. Using modified.", behandling.id)
        }
    }

    private fun getDVHBehandlingId(behandling: Behandling): String? {
        val fagsakId = behandling.fagsakId
        return if (behandling.fagsystem == Fagsystem.IT01 && fagsakId != null) {
            try {
                fagsakId.substring(startIndex = 0, endIndex = 4) + "-" +
                    fagsakId.substring(startIndex = 4, endIndex = 5) + "-" +
                    fagsakId.substring(5)
            } catch (e: Exception) {
                logger.error("Error while generating Infotrygd behandlingId for DVH v2, for behandling with id ${behandling.id}", e)
                fagsakId
            }
        } else {
            behandling.dvhReferanse ?: behandling.kildeReferanse
        }
    }

    private fun getBehandlingType(type: Type): BehandlingTypeV2 =
        when (type) {
            Type.KLAGE -> BehandlingTypeV2.KLAGE
            Type.ANKE_FOER_2027 -> BehandlingTypeV2.ANKE_FOER_2027
            Type.ANKE_ETTER_2027 -> BehandlingTypeV2.ANKE_ETTER_2027
            Type.ANKE_I_TRYGDERETTEN_FOER_2027 -> BehandlingTypeV2.ANKE_I_TRYGDERETTEN_FOER_2027
            Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> BehandlingTypeV2.ANKE_I_TRYGDERETTEN_ETTER_2027
            Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027 -> BehandlingTypeV2.BEGJAERING_OM_GJENOPPTAK_FOER_2027
            Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027 -> BehandlingTypeV2.BEGJAERING_OM_GJENOPPTAK_ETTER_2027
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027 -> BehandlingTypeV2.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027 -> BehandlingTypeV2.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027
            Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> BehandlingTypeV2.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET
            Type.OMGJOERINGSKRAV -> BehandlingTypeV2.OMGJOERINGSKRAV
        }

    private fun getResultat(behandling: Behandling): String? =
        if (behandling.feilregistrering != null) {
            "Feilregistrert"
        } else if (behandling.ferdigstilling != null) {
            behandling.utfall?.name
        } else {
            null
        }

    private fun getPart(
        type: PartIdType,
        value: String,
    ) = StatistikkTilDVHV2.Part(
        verdi = value,
        type =
            when (type) {
                PartIdType.PERSON -> StatistikkTilDVHV2.PartIdType.PERSON
                PartIdType.VIRKSOMHET -> StatistikkTilDVHV2.PartIdType.VIRKSOMHET
            },
    )

    private fun Registreringshjemmel.toSearchableString(): String = "${lovKilde.navn}-$spesifikasjon"
}
