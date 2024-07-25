package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDate
import java.time.LocalDateTime

object AnkebehandlingSetters {
    fun Ankebehandling.setVarsletFrist(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = varsletFrist
        val tidspunkt = LocalDateTime.now()
        varsletFrist = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.VARSLET_FRIST,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Ankebehandling.setVarsletBehandlingstidUnits(
        nyVerdi: Int,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = varsletBehandlingstidUnits
        val tidspunkt = LocalDateTime.now()
        varsletBehandlingstidUnits = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.VARSLET_BEHANDLINGSTID_UNITS,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Ankebehandling.setVarsletBehandlingstidUnitType(
        nyVerdi: TimeUnitType,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = varsletBehandlingstidUnitType
        val tidspunkt = LocalDateTime.now()
        varsletBehandlingstidUnitType = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.VARSLET_BEHANDLINGSTID_UNIT_TYPE,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }
}