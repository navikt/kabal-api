package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object GjenopptakITrygderettenbehandlingEtter2027Setters {
    fun GjenopptakITrygderettenbehandlingEtter2027.setNyGjenopptaksbehandlingKA(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String,
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
            changeList = listOfNotNull(change),
        )
    }
}
