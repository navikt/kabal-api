package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDate
import java.time.LocalDateTime

object AnkebehandlingSetters {

    fun Ankebehandling.setVarsletBehandlingstid(
        nyVerdiVarsletBehandlingstidUnits: Int,
        nyVerdiVarsletBehandlingstidUnitType: TimeUnitType,
        nyVerdiVarsletFrist: LocalDate,
        saksbehandlerident: String,
        mottakere: List<PartId>
    ): BehandlingEndretEvent {
        val gammelVerdiVarsletBehandlingstidUnits = varsletBehandlingstidUnits
        val gammelVerdiVarsletBehandlingstidUnitType = varsletBehandlingstidUnitType
        val gammelVerdiVarsletFrist = varsletFrist
        val tidspunkt = LocalDateTime.now()

        if (varsletBehandlingstidHistorikk.isEmpty()) {
            recordVarsletBehandlingstidHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                mottakere = listOf(),
            )
        }

        varsletBehandlingstidUnits = nyVerdiVarsletBehandlingstidUnits
        varsletBehandlingstidUnitType = nyVerdiVarsletBehandlingstidUnitType
        varsletFrist = nyVerdiVarsletFrist

        recordVarsletBehandlingstidHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = saksbehandlerident,
            mottakere = mottakere,
        )

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

    private fun Ankebehandling.recordVarsletBehandlingstidHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        mottakere: List<PartId>,
    ) {
        varsletBehandlingstidHistorikk.add(
            VarsletBehandlingstidHistorikk(
                mottakerList = mottakere.map { it.toVarsletBehandlingstidHistorikkMottaker() },
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                varsletFrist = varsletFrist,
                varsletBehandlingstidUnits = varsletBehandlingstidUnits,
                varsletBehandlingstidUnitType = varsletBehandlingstidUnitType,
            )
        )
    }
}