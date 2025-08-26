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
        TODO("Not yet implemented")
    }
}
