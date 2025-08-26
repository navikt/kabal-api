package no.nav.klage.kaptein.service

import no.nav.klage.kaptein.api.view.AnonymousBehandlingListView
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.stereotype.Service

@Service
class KapteinService(
    private val behandlingRepository: BehandlingRepository,
) {

    fun getBehandlinger(): AnonymousBehandlingListView {
        val behandlinger = behandlingRepository.findAll()
        return AnonymousBehandlingListView(
            anonymizedBehandlingList = behandlinger.map { it.toAnonymousBehandlingView() },
            total = behandlinger.size,
        )
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
        TODO("Not yet implemented")
    }

    private fun mapBehandlingEtterTROpphevetToAnonymousBehandlingView(behandling: BehandlingEtterTrygderettenOpphevet): AnonymousBehandlingView {
        TODO("Not yet implemented")
    }

    private fun mapAnkebehandlingToAnonymousBehandlingView(behandling: Ankebehandling): AnonymousBehandlingView {
        TODO("Not yet implemented")
    }

    private fun mapAnkeITrygderettenbehandlingToAnonymousBehandlingView(behandling: AnkeITrygderettenbehandling): AnonymousBehandlingView {
        TODO("Not yet implemented")
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
            timesPreviouslyExtended = behandling.getTimesPreviouslyExtended(),
        )

    }

    private fun Behandling.mapToVedtakView(): AnonymousBehandlingView.VedtakView {
        return AnonymousBehandlingView.VedtakView(
            id = id,
            utfallId = utfall?.id,
            extraUtfallIdSet = extraUtfallSet.map { it.id }.toSet(),
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
