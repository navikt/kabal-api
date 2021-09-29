package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.kafka.KlageStatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.KlagebehandlingState
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.repositories.EndringsloggRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StatistikkTilDVHService(
    private val statistikkTilDVHKafkaProducer: StatistikkTilDVHKafkaProducer,
    private val mottakRepository: MottakRepository,
    private val endringsloggRepository: EndringsloggRepository
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Transactional(readOnly = true)
    fun process(klagebehandling: Klagebehandling, endringslogginnslag: List<Endringslogginnslag>? = null) {

        val endringslogg = endringslogginnslag ?: endringsloggRepository.findByKlagebehandlingIdOrderByTidspunktDesc(
            klagebehandling.id
        )
        val mottak = mottakRepository.getOne(klagebehandling.mottakId)

        if (shouldSendStats(endringslogg)) {
            val klageStatistikkTilDVH = createKlageStatistikkTilDVH(
                klagebehandling,
                mottak,
                getKlagebehandlingState(endringslogg)
            )
            statistikkTilDVHKafkaProducer.sendStatistikkTilDVH(klageStatistikkTilDVH)
        }
    }

    private fun shouldSendStats(endringslogginnslag: List<Endringslogginnslag>) =
        endringslogginnslag.isEmpty() ||
                endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT || it.felt === Felt.VEDTAK_SLUTTFOERT }

    private fun getKlagebehandlingState(endringslogginnslag: List<Endringslogginnslag>): KlagebehandlingState {
        return when {
            endringslogginnslag.isEmpty() -> KlagebehandlingState.MOTTATT
            endringslogginnslag.any { it.felt === Felt.TILDELT_SAKSBEHANDLERIDENT } -> KlagebehandlingState.TILDELT_SAKSBEHANDLER
            endringslogginnslag.any { it.felt === Felt.VEDTAK_SLUTTFOERT } -> KlagebehandlingState.AVSLUTTET
            else -> KlagebehandlingState.UKJENT.also {
                logger.warn(
                    "unknown state for klagebehandling with id {}",
                    endringslogginnslag.first().klagebehandlingId
                )
            }
        }
    }

    private fun createKlageStatistikkTilDVH(
        klagebehandling: Klagebehandling,
        mottak: Mottak,
        klagebehandlingState: KlagebehandlingState
    ): KlageStatistikkTilDVH {
        val vedtak = klagebehandling.getVedtakOrException()

        val funksjoneltEndringstidspunkt =
            getFunksjoneltEndringstidspunkt(klagebehandling, klagebehandlingState, vedtak)

        return KlageStatistikkTilDVH(
            behandlingId = mottak.dvhReferanse ?: mottak.kildeReferanse,
            behandlingIdKabal = klagebehandling.id.toString(),
            behandlingStartetKA = klagebehandling.tildeling?.tidspunkt?.toLocalDate(),
            behandlingStatus = klagebehandlingState,
            behandlingType = klagebehandling.type.navn,
            beslutter = klagebehandling.medunderskriver?.saksbehandlerident,
            endringstid = funksjoneltEndringstidspunkt,
            hjemmel = klagebehandling.hjemler.map { it.toSearchableString() },
            klager = getPart(klagebehandling.klager.partId.type, klagebehandling.klager.partId.value),
            omgjoeringsgrunn = vedtak.grunn?.navn,
            opprinneligFagsaksystem = mottak.kildesystem.navn,
            overfoertKA = mottak.created.toLocalDate(),
            resultat = vedtak.utfall?.navn,
            sakenGjelder = getPart(klagebehandling.sakenGjelder.partId.type, klagebehandling.sakenGjelder.partId.value),
            saksbehandler = klagebehandling.tildeling?.saksbehandlerident,
            saksbehandlerEnhet = klagebehandling.tildeling?.enhet,
            tekniskTid = klagebehandling.modified,
            vedtakId = vedtak.id.toString(),
            vedtaksdato = vedtak.ferdigstiltIJoark?.toLocalDate(),
            ytelseType = "TODO",
            kvalitetsvurdering = klagebehandling.toKvalitetsvurdering()
        )
    }

    private fun getFunksjoneltEndringstidspunkt(
        klagebehandling: Klagebehandling,
        klagebehandlingState: KlagebehandlingState,
        vedtak: Vedtak?
    ): LocalDateTime {
        return when (klagebehandlingState) {
            KlagebehandlingState.MOTTATT -> klagebehandling.mottattKlageinstans
            KlagebehandlingState.TILDELT_SAKSBEHANDLER -> klagebehandling.tildeling?.tidspunkt
                ?: throw RuntimeException("tildelt mangler")
            KlagebehandlingState.AVSLUTTET -> vedtak?.ferdigstiltIJoark
                ?: throw RuntimeException("ferdigstiltIJoark mangler")
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

    private fun Klagebehandling.toKvalitetsvurdering(): KlageStatistikkTilDVH.Kvalitetsvurdering? {
        when {
            kvalitetsvurdering != null -> {
                return KlageStatistikkTilDVH.Kvalitetsvurdering(
                    kvalitetOversendelsesbrevBra = kvalitetsvurdering!!.oversendelsesbrevBra,
                    kvalitetsavvikOversendelsesbrev = kvalitetsvurdering!!.kvalitetsavvikOversendelsesbrev.map { it.name }
                        .map { KlageStatistikkTilDVH.Kvalitetsvurdering.KvalitetsavvikOversendelsesbrev.valueOf(it) }
                        .toSet(),
                    kvalitetUtredningBra = kvalitetsvurdering!!.utredningBra,
                    kvalitetsavvikUtredning = kvalitetsvurdering!!.kvalitetsavvikUtredning.map { it.name }
                        .map { KlageStatistikkTilDVH.Kvalitetsvurdering.KvalitetsavvikUtredning.valueOf(it) }.toSet(),
                    kvalitetVedtaketBra = kvalitetsvurdering!!.vedtakBra,
                    kvalitetsavvikVedtak = kvalitetsvurdering!!.kvalitetsavvikVedtak.map { it.name }
                        .map { KlageStatistikkTilDVH.Kvalitetsvurdering.KvalitetsavvikVedtak.valueOf(it) }.toSet(),
                    avvikStorKonsekvens = kvalitetsvurdering!!.avvikStorKonsekvens
                )
            }
            else -> {
                return null
            }
        }
    }
}