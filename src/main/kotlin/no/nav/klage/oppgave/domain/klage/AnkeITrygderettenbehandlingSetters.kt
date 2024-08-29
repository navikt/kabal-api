package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDateTime

object AnkeITrygderettenbehandlingSetters {

    fun AnkeITrygderettenbehandling.setSendtTilTrygderetten(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = sendtTilTrygderetten
        val tidspunkt = LocalDateTime.now()
        sendtTilTrygderetten = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.SENDT_TIL_TRYGDERETTEN_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(
            behandling = this,
            endringslogginnslag = listOfNotNull(endringslogg)
        )
    }

    fun AnkeITrygderettenbehandling.setKjennelseMottatt(
        nyVerdi: LocalDateTime?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = kjennelseMottatt
        val tidspunkt = LocalDateTime.now()
        kjennelseMottatt = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.KJENNELSE_MOTTATT_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(
            behandling = this,
            endringslogginnslag = listOfNotNull(endringslogg)
        )
    }

    fun AnkeITrygderettenbehandling.setNyAnkebehandlingKA(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = nyAnkebehandlingKA
        val tidspunkt = LocalDateTime.now()
        nyAnkebehandlingKA = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.NY_ANKEBEHANDLING_KA,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(
            behandling = this,
            endringslogginnslag = listOfNotNull(endringslogg)
        )
    }

    fun AnkeITrygderettenbehandling.setNyBehandlingEtterTROpphevet(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = nyBehandlingEtterTROpphevet
        val tidspunkt = LocalDateTime.now()
        nyBehandlingEtterTROpphevet = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.NY_BEHANDLING_ETTER_TR_OPPHEVET,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(
            behandling = this,
            endringslogginnslag = listOfNotNull(endringslogg)
        )
    }
}
