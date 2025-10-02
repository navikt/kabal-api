package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object GjenopptakITrygderettenbehandlingSetters {

    fun GjenopptakITrygderettenbehandling.setSendtTilTrygderetten(
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

    fun GjenopptakITrygderettenbehandling.setKjennelseMottatt(
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

    fun GjenopptakITrygderettenbehandling.setNyGjenopptaksbehandlingKA(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingChangedEvent {
        val gammelVerdi = nyGjenopptaksbehandlingKA
        val tidspunkt = LocalDateTime.now()
        nyGjenopptaksbehandlingKA = nyVerdi
        modified = tidspunkt
        val change =
            createChange(
                saksbehandlerident = saksbehandlerident,
                felt = BehandlingChangedEvent.Felt.NY_GJENOPPTAKSBEHANDLING_KA,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
            )
        return BehandlingChangedEvent(
            behandling = this,
            changeList = listOfNotNull(change)
        )
    }

    fun GjenopptakITrygderettenbehandling.setNyBehandlingEtterTROpphevet(
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
