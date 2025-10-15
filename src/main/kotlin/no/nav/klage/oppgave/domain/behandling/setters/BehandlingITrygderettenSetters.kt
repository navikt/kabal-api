package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingITrygderetten
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object BehandlingITrygderettenSetters {

    fun BehandlingITrygderetten.setSendtTilTrygderetten(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        this as Behandling
        val gammelVerdi = sendtTilTrygderetten
        val tidspunkt = LocalDateTime.now()
        sendtTilTrygderetten = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.SENDT_TIL_TRYGDERETTEN_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
            )
        return BehandlingChangedEvent(
            behandling = this,
            changeList = listOfNotNull(change)
        )
    }

    fun BehandlingITrygderetten.setKjennelseMottatt(
        nyVerdi: LocalDateTime?,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        this as Behandling
        val gammelVerdi = kjennelseMottatt
        val tidspunkt = LocalDateTime.now()
        kjennelseMottatt = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.KJENNELSE_MOTTATT_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
            )
        return BehandlingChangedEvent(
            behandling = this,
            changeList = listOfNotNull(change)
        )
    }

    fun BehandlingITrygderetten.setNyBehandlingEtterTROpphevet(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        this as Behandling
        val gammelVerdi = nyBehandlingEtterTROpphevet
        val tidspunkt = LocalDateTime.now()
        nyBehandlingEtterTROpphevet = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.NY_BEHANDLING_ETTER_TR_OPPHEVET,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
            )
        return BehandlingChangedEvent(
            behandling = this,
            changeList = listOfNotNull(change)
        )
    }
}
