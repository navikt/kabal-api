package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object AnkeITrygderettenbehandlingSetters {

    fun AnkeITrygderettenbehandling.setSendtTilTrygderetten(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
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

    fun AnkeITrygderettenbehandling.setKjennelseMottatt(
        nyVerdi: LocalDateTime?,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
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

    fun AnkeITrygderettenbehandling.setNyAnkebehandlingKA(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = nyAnkebehandlingKA
        val tidspunkt = LocalDateTime.now()
        nyAnkebehandlingKA = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.NY_ANKEBEHANDLING_KA,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
            )
        return BehandlingChangedEvent(
            behandling = this,
            changeList = listOfNotNull(change)
        )
    }

    fun AnkeITrygderettenbehandling.setNyBehandlingEtterTROpphevet(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
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
