package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import java.time.LocalDateTime

object AnkeITrygderettenbehandlingSetters {
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
}
