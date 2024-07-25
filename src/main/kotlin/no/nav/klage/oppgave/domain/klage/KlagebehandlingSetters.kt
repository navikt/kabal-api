package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDate
import java.time.LocalDateTime

object KlagebehandlingSetters {

    fun Klagebehandling.setMottattVedtaksinstans(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = mottattVedtaksinstans
        val tidspunkt = LocalDateTime.now()
        mottattVedtaksinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.MOTTATT_FOERSTEINSTANS_DATO,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Klagebehandling.setVarsletBehandlingstid(
        nyVerdiVarsletBehandlingstidUnits: Int,
        nyVerdiVarsletBehandlingstidUnitType: TimeUnitType,
        nyVerdiVarsletFrist: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdiVarsletBehandlingstidUnits = varsletBehandlingstidUnits
        val gammelVerdiVarsletBehandlingstidUnitType = varsletBehandlingstidUnitType
        val gammelVerdiVarsletFrist = varsletFrist
        val tidspunkt = LocalDateTime.now()

        varsletBehandlingstidUnits = nyVerdiVarsletBehandlingstidUnits
        varsletBehandlingstidUnitType = nyVerdiVarsletBehandlingstidUnitType
        varsletFrist = nyVerdiVarsletFrist

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.VARSLET_FRIST,
            fraVerdi = gammelVerdiVarsletFrist.toString(),
            tilVerdi = nyVerdiVarsletFrist.toString(),
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.VARSLET_BEHANDLINGSTID_UNITS,
            fraVerdi = gammelVerdiVarsletBehandlingstidUnits.toString(),
            tilVerdi = nyVerdiVarsletBehandlingstidUnits.toString(),
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.VARSLET_BEHANDLINGSTID_UNIT_TYPE,
            fraVerdi = gammelVerdiVarsletBehandlingstidUnitType.toString(),
            tilVerdi = nyVerdiVarsletBehandlingstidUnitType.toString(),
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }
}