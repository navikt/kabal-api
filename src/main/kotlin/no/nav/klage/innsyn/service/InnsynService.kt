package no.nav.klage.innsyn.service

import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.kodeverk.innsendingsytelse.Innsendingsytelse
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.stereotype.Service

@Service
class InnsynService(
    private val behandlingRepository: BehandlingRepository,
) {

    fun getSakerForBruker(fnr: String): InnsynResponse {
        val activeBehandlinger =
            behandlingRepository.findBySakenGjelderPartIdValueAndFerdigstillingIsNullAndFeilregistreringIsNull(fnr)

        val finishedBehandlinger =
            behandlingRepository.findBySakenGjelderPartIdValueAndFerdigstillingIsNotNullAndFeilregistreringIsNull(fnr)

        return InnsynResponse(
            active = activeBehandlinger.map { it.toSakView() },
            finished = finishedBehandlinger.map { it.toSakView() }
        )
    }

    private fun Behandling.toSakView(): SakView {
        return SakView(
            id = "${type.id}_${fagsystem.id}_${fagsakId}",
            typeId = type.name,
            saksnummer = fagsakId,
            ytelseId = ytelse.id,
            innsendingsytelseId = Innsendingsytelse.FORELDREPENGER.id,//TODO
            events = getEvents()
        )
    }

    private fun Behandling.getEvents(): List<SakView.Event> {
        return when (this) {
            is Klagebehandling -> {
                val events = mutableListOf<SakView.Event>()

                events += SakView.Event(
                    type = SakView.Event.EventType.MOTTATT_VEDTAKSINSTANS,
                    date = mottattVedtaksinstans.atStartOfDay(),
                )

                events += SakView.Event(
                    type = SakView.Event.EventType.MOTTATT_KA,
                    date = mottattKlageinstans,
                )

                if (ferdigstilling != null) {
                    events += SakView.Event(
                        type = SakView.Event.EventType.FERDIG_KA,
                        date = ferdigstilling!!.avsluttetAvSaksbehandler,
                    )
                }

                events
            }

            is AnkeITrygderettenbehandling -> emptyList() //TODO
            is Ankebehandling -> emptyList() //TODO
            is BehandlingEtterTrygderettenOpphevet -> emptyList() //TODO
            is Omgjoeringskravbehandling -> emptyList() //TODO
        }
    }
}