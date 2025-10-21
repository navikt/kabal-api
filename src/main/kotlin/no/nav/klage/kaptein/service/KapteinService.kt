package no.nav.klage.kaptein.service

import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

@Service
@Transactional
class KapteinService(
    private val behandlingRepository: BehandlingRepository,
    private val entityManager: EntityManager,
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${KAPTEIN_BEHANDLING_TOPIC}")
    private val kapteinBehandlingTopic: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapper = ourJacksonObjectMapper()
    }

    @Transactional(readOnly = true)
    fun writeBehandlingerStreamedToOutputStreamAsNDJson(httpServletResponse: HttpServletResponse) {
        val start = System.currentTimeMillis()
        val startCount = System.currentTimeMillis()
        val total = behandlingRepository.count()

        val behandlingYtelseCounter = mutableMapOf<String, Int>()
        val viewYtelseCounter = mutableMapOf<String, Int>()

        logger.debug("Counted total behandlinger: $total in ${System.currentTimeMillis() - startCount} ms")
        behandlingRepository.findAllForKapteinStreamed().use { streamed ->
            val outputStream: OutputStream = httpServletResponse.outputStream
            val writer = BufferedWriter(OutputStreamWriter(outputStream))
            httpServletResponse.contentType = MediaType.APPLICATION_NDJSON_VALUE
            httpServletResponse.status = HttpStatus.OK.value()
            httpServletResponse.addIntHeader("Kaptein-Total", total.toInt())
            httpServletResponse.addHeader("Transfer-Encoding", "chunked")

            var count = 0
            streamed.forEach { behandling ->
                val view = behandling.toAnonymousBehandlingView()
                behandlingYtelseCounter[behandling.ytelse.id] =
                    behandlingYtelseCounter.getOrDefault(behandling.ytelse.id, 0) + 1
                viewYtelseCounter[view.ytelseId] = viewYtelseCounter.getOrDefault(view.ytelseId, 0) + 1
                writer.write(objectMapper.writeValueAsString(view) + "\n")
                entityManager.detach(behandling)
                if (count++ % 100 == 0) {
                    writer.flush()
                }
            }
            val end = System.currentTimeMillis()
            logger.debug("Fetched and wrote $total behandlinger to output stream in ${end - start} ms")
            writer.flush()
            writer.close()
        }
        logger.debug("Behandling ytelse counts: $behandlingYtelseCounter")
        logger.debug("View ytelse counts: $viewYtelseCounter")
    }

    fun sendBehandlingChanged(behandling: Behandling) {
        publishToKafkaTopic(
            key = behandling.id.toString(),
            json = objectMapper.writeValueAsString(behandling.toAnonymousBehandlingView()),
        )
    }

    private fun publishToKafkaTopic(key: String, json: String?) {
        logger.debug("Sending to Kafka topic: {}", kapteinBehandlingTopic)
        runCatching {
            aivenKafkaTemplate.send(kapteinBehandlingTopic, key, json).get()
            logger.debug("Payload sent to Kafka.")
        }.onFailure {
            logger.error("Could not send payload to Kafka", it)
        }
    }

    private fun Behandling.toAnonymousBehandlingView(): AnonymousBehandlingView {
        return when (this) {
            is Klagebehandling -> mapKlagebehandlingToAnonymousBehandlingView(this)
            is Ankebehandling -> mapAnkebehandlingToAnonymousBehandlingView(this)
            is AnkeITrygderettenbehandling, is GjenopptakITrygderettenbehandling -> mapBehandlingITrygderettenToAnonymousBehandlingView(this)
            is BehandlingEtterTrygderettenOpphevet -> mapBehandlingEtterTROpphevetToAnonymousBehandlingView(this)
            is Omgjoeringskravbehandling -> mapOmgjoeringskravbehandlingToAnonymousBehandlingView(this)
            is Gjenopptaksbehandling -> mapGjenopptaksbehandlingToAnonymousBehandlingView(this)
        }
    }

    private fun mapOmgjoeringskravbehandlingToAnonymousBehandlingView(behandling: Omgjoeringskravbehandling): AnonymousBehandlingView {
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
    }

    private fun mapBehandlingEtterTROpphevetToAnonymousBehandlingView(behandling: BehandlingEtterTrygderettenOpphevet): AnonymousBehandlingView {
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
    }

    private fun mapAnkebehandlingToAnonymousBehandlingView(behandling: Ankebehandling): AnonymousBehandlingView {
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
    }

    private fun mapKlagebehandlingToAnonymousBehandlingView(behandling: Klagebehandling): AnonymousBehandlingView {
        return AnonymousBehandlingView(
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
    }

    private fun mapGjenopptaksbehandlingToAnonymousBehandlingView(behandling: Gjenopptaksbehandling): AnonymousBehandlingView {
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
    }

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

    private fun Behandling.mapToVedtakView(): AnonymousBehandlingView.VedtakView {
        return AnonymousBehandlingView.VedtakView(
            id = id,
            utfallId = utfall?.id,
            hjemmelIdSet = registreringshjemler.map { it.id }.toSet(),
        )
    }

    private fun Feilregistrering?.toView(): AnonymousBehandlingView.FeilregistreringView? {
        return this?.let {
            AnonymousBehandlingView.FeilregistreringView(
                registered = it.registered,
                reason = it.reason,
                fagsystemId = it.fagsystem.id
            )
        }
    }
}
