package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.events.KlagebehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag.Companion.endringslogg
import java.time.LocalDate
import java.time.LocalDateTime

object KlagebehandlingAggregatFunctions {

    fun Klagebehandling.setMottattFoersteinstans(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): KlagebehandlingEndretEvent {
        val gammelVerdi = mottattFoersteinstans
        val tidspunkt = LocalDateTime.now()
        mottattFoersteinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.DATO_MOTTATT_FOERSTEINSTANS,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                behandlingId = this.id,
                tidspunkt = tidspunkt,
            )
        return KlagebehandlingEndretEvent(klagebehandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }
}