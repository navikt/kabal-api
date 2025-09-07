package no.nav.klage.kaptein.service

import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapper = ourJacksonObjectMapper()
    }

    @Transactional(readOnly = true)
    fun writeBehandlingerStreamedToOutputStream(httpServletResponse: HttpServletResponse) {
        val start = System.currentTimeMillis()
        val startCount = System.currentTimeMillis()
        val total = behandlingRepository.count()
        logger.debug("Counted total behandlinger: $total in ${System.currentTimeMillis() - startCount} ms")
        behandlingRepository.findAllForKapteinStreamed().use { streamed ->
            //write directly to output stream
            val outputStream: OutputStream = httpServletResponse.outputStream
            val writer = BufferedWriter(OutputStreamWriter(outputStream))
            httpServletResponse.contentType = MediaType.APPLICATION_NDJSON_VALUE
            httpServletResponse.status = HttpStatus.OK.value()
            writer.write("{\"anonymizedBehandlingList\":\n[\n")
            var count = 1
            streamed.forEach { behandling ->
                writer.write(objectMapper.writeValueAsString(behandling.toAnonymousBehandlingView()))
                if (count++ < total) {
                    writer.write(",\n")
                }
                entityManager.detach(behandling)
            }
            writer.write("\n],\n")
            writer.write("\"total\": ${total}\n")
            writer.write("}\n")
            val end = System.currentTimeMillis()
            logger.debug("Fetched and wrote $total behandlinger to output stream in ${end - start} ms")
            writer.flush()
            writer.close()
        }
    }

    private fun Behandling.toAnonymousBehandlingView(): AnonymousBehandlingView {
        return when (this) {
            is Klagebehandling -> mapKlagebehandlingToAnonymousBehandlingView(this)
            is Ankebehandling -> mapAnkebehandlingToAnonymousBehandlingView(this)
            is AnkeITrygderettenbehandling -> mapAnkeITrygderettenbehandlingToAnonymousBehandlingView(this)
            is BehandlingEtterTrygderettenOpphevet -> mapBehandlingEtterTROpphevetToAnonymousBehandlingView(this)
            is Omgjoeringskravbehandling -> mapOmgjoeringskravbehandlingToAnonymousBehandlingView(this)
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
        )
    }

    private fun mapAnkeITrygderettenbehandlingToAnonymousBehandlingView(behandling: AnkeITrygderettenbehandling): AnonymousBehandlingView {
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
