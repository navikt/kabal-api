package no.nav.klage.kaptein.service

import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.BehandlingITrygderetten
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

@Service
@Transactional
class KapteinService(
    private val behandlingRepository: BehandlingRepository,
    private val entityManager: EntityManager,
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    @Value($$"${KAPTEIN_BEHANDLING_TOPIC}")
    private val kapteinBehandlingTopic: String,
    transactionManager: PlatformTransactionManager,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
        private const val KAPTEIN_BATCH_SIZE = 200
    }

    private val readOnlyTransactionTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun writeBehandlingerStreamedToOutputStreamAsNDJson(httpServletResponse: HttpServletResponse) {
        val start = System.currentTimeMillis()
        val startCount = System.currentTimeMillis()
        val behandlingIds =
            readOnlyTransactionTemplate.execute {
                behandlingRepository.findAllIdsForKaptein()
            }
        val total = behandlingIds.size

        val behandlingYtelseCounter = mutableMapOf<String, Int>()
        val viewYtelseCounter = mutableMapOf<String, Int>()

        logger.debug("Fetched IDs for $total behandlinger in ${System.currentTimeMillis() - startCount} ms")
        val outputStream: OutputStream = httpServletResponse.outputStream
        val writer = BufferedWriter(OutputStreamWriter(outputStream))
        httpServletResponse.contentType = MediaType.APPLICATION_NDJSON_VALUE
        httpServletResponse.status = HttpStatus.OK.value()
        httpServletResponse.addIntHeader("Kaptein-Total", total)
        httpServletResponse.addHeader("Transfer-Encoding", "chunked")

        writer.use {
            var count = 0
            behandlingIds.chunked(KAPTEIN_BATCH_SIZE).forEach { batchIds ->
                val views =
                    readOnlyTransactionTemplate.execute {
                        try {
                            behandlingRepository
                                .findAllForKapteinByIds(batchIds)
                                .map { behandling -> behandling.toAnonymousBehandlingView() }
                        } finally {
                            entityManager.clear()
                        }
                    }

                views.forEach { view ->
                    behandlingYtelseCounter[view.ytelseId] =
                        behandlingYtelseCounter.getOrDefault(key = view.ytelseId, defaultValue = 0) + 1
                    viewYtelseCounter[view.ytelseId] =
                        viewYtelseCounter.getOrDefault(key = view.ytelseId, defaultValue = 0) + 1
                    writer.write(jacksonObjectMapper.writeValueAsString(view) + "\n")
                    if (count++ % 100 == 0) {
                        writer.flush()
                    }
                }
            }
            val end = System.currentTimeMillis()
            logger.debug("Fetched and wrote $total behandlinger to output stream in ${end - start} ms")
            it.flush()
        }
        logger.debug("Behandling ytelse counts: {}", behandlingYtelseCounter)
        logger.debug("View ytelse counts: {}", viewYtelseCounter)
    }

    fun sendBehandlingChanged(behandling: Behandling) {
        publishToKafkaTopic(
            key = behandling.id.toString(),
            json = jacksonObjectMapper.writeValueAsString(behandling.toAnonymousBehandlingView()),
        )
    }

    private fun publishToKafkaTopic(
        key: String,
        json: String?,
    ) {
        logger.debug("Sending to Kafka topic: {}", kapteinBehandlingTopic)
        runCatching {
            aivenKafkaTemplate.send(kapteinBehandlingTopic, key, json).get()
            logger.debug("Payload sent to Kafka.")
        }.onFailure {
            logger.error("Could not send payload to Kafka", it)
        }
    }

    private fun Behandling.toAnonymousBehandlingView(): AnonymousBehandlingView =
        when (this) {
            is Klagebehandling -> {
                mapKlagebehandlingToAnonymousBehandlingView(this)
            }

            is AnkebehandlingFoer2027 -> {
                mapAnkebehandlingFoer2027ToAnonymousBehandlingView(this)
            }

            is AnkebehandlingEtter2027 -> {
                mapAnkebehandlingEtter2027ToAnonymousBehandlingView(this)
            }

            is AnkeITrygderettenbehandlingFoer2027,
            is AnkeITrygderettenbehandlingEtter2027,
            is GjenopptakITrygderettenbehandlingFoer2027,
            is GjenopptakITrygderettenbehandlingEtter2027,
            -> {
                mapBehandlingITrygderettenToAnonymousBehandlingView(
                    this,
                )
            }

            is BehandlingEtterTrygderettenOpphevet -> {
                mapBehandlingEtterTROpphevetToAnonymousBehandlingView(this)
            }

            is Omgjoeringskravbehandling -> {
                mapOmgjoeringskravbehandlingToAnonymousBehandlingView(this)
            }

            is GjenopptaksbehandlingFoer2027 -> {
                mapGjenopptaksbehandlingFoer2027ToAnonymousBehandlingView(this)
            }

            is GjenopptaksbehandlingEtter2027 -> {
                mapGjenopptaksbehandlingEtter2027ToAnonymousBehandlingView(this)
            }

            else -> {
                error("Unknown Behandling subtype: ${this::class.java.name}")
            }
        }

    private fun mapOmgjoeringskravbehandlingToAnonymousBehandlingView(behandling: Omgjoeringskravbehandling): AnonymousBehandlingView =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapBehandlingEtterTROpphevetToAnonymousBehandlingView(
        behandling: BehandlingEtterTrygderettenOpphevet,
    ): AnonymousBehandlingView =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = behandling.kjennelseMottatt,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapAnkebehandlingFoer2027ToAnonymousBehandlingView(behandling: AnkebehandlingFoer2027) =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapAnkebehandlingEtter2027ToAnonymousBehandlingView(behandling: AnkebehandlingEtter2027) =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = null,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapKlagebehandlingToAnonymousBehandlingView(behandling: Klagebehandling): AnonymousBehandlingView =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = behandling.avsenderEnhetFoersteinstans,
            mottattVedtaksinstans = behandling.mottattVedtaksinstans,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapGjenopptaksbehandlingFoer2027ToAnonymousBehandlingView(
        behandling: GjenopptaksbehandlingFoer2027,
    ): AnonymousBehandlingView =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = behandling.varsletBehandlingstid?.varsletFrist,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapGjenopptaksbehandlingEtter2027ToAnonymousBehandlingView(
        behandling: GjenopptaksbehandlingEtter2027,
    ): AnonymousBehandlingView =
        AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = null,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = null,
            kjennelseMottatt = null,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = null,
            previousRegistreringshjemmelIdList = null,
            initiatingSystem = behandling.initiatingSystem,
        )

    private fun mapBehandlingITrygderettenToAnonymousBehandlingView(behandling: BehandlingITrygderetten): AnonymousBehandlingView {
        behandling as Behandling

        val previousBehandling: Behandling? =
            behandling.previousBehandlingId?.let { id -> behandlingRepository.findByIdForKaptein(id) }

        return AnonymousBehandlingView(
            id = behandling.id,
            fraNAVEnhet = null,
            mottattVedtaksinstans = null,
            temaId = behandling.ytelse.toTema().id,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            avsluttetAvSaksbehandlerDate = behandling.ferdigstilling?.avsluttetAvSaksbehandler?.toLocalDate(),
            isAvsluttetAvSaksbehandler = behandling.ferdigstilling != null,
            frist = behandling.frist,
            ageKA = behandling.toAgeInDays(),
            datoSendtMedunderskriver = behandling.medunderskriver?.tidspunkt?.toLocalDate(),
            hjemmelIdList = behandling.hjemler.map { it.id },
            modified = behandling.modified,
            created = behandling.created,
            resultat = behandling.mapToVedtakView(),
            sattPaaVent = behandling.sattPaaVent,
            feilregistrering = behandling.feilregistrering.toView(),
            fagsystemId = behandling.fagsystem.id,
            varsletFrist = null,
            tilbakekreving = behandling.tilbakekreving,
            sendtTilTrygderetten = behandling.sendtTilTrygderetten,
            kjennelseMottatt = behandling.kjennelseMottatt,
            isTildelt = !behandling.isFerdigstiltOrFeilregistrert() && behandling.tildeling != null,
            tildeltEnhet = behandling.tildeling?.enhet,
            previousTildeltEnhet = previousBehandling?.tildeling?.enhet,
            previousRegistreringshjemmelIdList = previousBehandling?.registreringshjemler?.map { it.id },
            initiatingSystem = behandling.initiatingSystem,
        )
    }

    private fun Behandling.mapToVedtakView(): AnonymousBehandlingView.VedtakView =
        AnonymousBehandlingView.VedtakView(
            id = id,
            utfallId = utfall?.id,
            hjemmelIdSet = registreringshjemler.map { it.id }.toSet(),
        )

    private fun Feilregistrering?.toView(): AnonymousBehandlingView.FeilregistreringView? =
        this?.let {
            AnonymousBehandlingView.FeilregistreringView(
                registered = it.registered,
                reason = it.reason,
                fagsystemId = it.fagsystem.id,
            )
        }
}
