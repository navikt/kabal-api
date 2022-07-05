package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import java.time.LocalDateTime

data class AnkeITrygderettenbehandlingInput(
    val klager: Klager,
    val sakenGjelder: SakenGjelder? = null,
    val ytelse: Ytelse,
    val type: Type,
    val kildeReferanse: String,
    val dvhReferanse: String,
    val sakFagsystem: Fagsystem,
    val sakFagsakId: String? = null,
    val sakMottattKlageinstans: LocalDateTime,
    val saksdokumenter: MutableSet<Saksdokument>,
    val innsendingsHjemler: MutableSet<Hjemmel>,
    val kildesystem: Fagsystem,
    val sendtTilTrygderetten: LocalDateTime,
    val registreringsHjemmelSet: MutableSet<Registreringshjemmel>
)
