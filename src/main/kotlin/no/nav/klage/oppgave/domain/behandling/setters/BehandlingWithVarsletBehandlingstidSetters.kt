package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.embedded.Mottaker
import no.nav.klage.oppgave.domain.behandling.embedded.VarsletBehandlingstid
import no.nav.klage.oppgave.domain.behandling.historikk.VarsletBehandlingstidHistorikk
import no.nav.klage.oppgave.domain.behandling.historikk.toVarsletBehandlingstidHistorikkMottaker
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object BehandlingWithVarsletBehandlingstidSetters {
    fun BehandlingWithVarsletBehandlingstid.setVarsletBehandlingstid(
        varsletBehandlingstid: VarsletBehandlingstid,
        saksbehandlerident: String,
        saksbehandlernavn: String,
        mottakere: List<Mottaker>,
    ): BehandlingChangedEvent {
        val gammelVerdi = this.varsletBehandlingstid

        val tidspunkt = LocalDateTime.now()

        if (varsletBehandlingstidHistorikk.isEmpty()) {
            recordVarsletBehandlingstidHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
                mottakere = listOf(),
            )
        }
        this.varsletBehandlingstid = varsletBehandlingstid

        recordVarsletBehandlingstidHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = saksbehandlerident,
            utfoerendeNavn = saksbehandlernavn,
            mottakere = mottakere,
        )

        val changeList = mutableListOf<BehandlingChangedEvent.Change>()

        createChange(
            saksbehandlerident = saksbehandlerident,
            felt = BehandlingChangedEvent.Felt.VARSLET_FRIST,
            fraVerdi = gammelVerdi.toString(),
            tilVerdi = varsletBehandlingstid.toString(),
            behandlingId = this.id,
        )?.let { changeList.add(it) }

        return BehandlingChangedEvent(behandling = this as Behandling, changeList = changeList)
    }

    private fun BehandlingWithVarsletBehandlingstid.recordVarsletBehandlingstidHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        utfoerendeNavn: String?,
        mottakere: List<Mottaker>,
    ) {
        varsletBehandlingstidHistorikk.add(
            VarsletBehandlingstidHistorikk(
                mottakerList = mottakere.map { it.toVarsletBehandlingstidHistorikkMottaker() },
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                utfoerendeNavn = utfoerendeNavn,
                varsletBehandlingstid = varsletBehandlingstid,
            )
        )

    }
}
