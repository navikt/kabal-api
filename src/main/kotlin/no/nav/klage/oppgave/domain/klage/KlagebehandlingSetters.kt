package no.nav.klage.oppgave.domain.klage

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
}