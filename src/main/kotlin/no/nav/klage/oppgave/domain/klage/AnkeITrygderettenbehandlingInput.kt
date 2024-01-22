package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import java.time.LocalDateTime

data class AnkeITrygderettenbehandlingInput(
    val klager: Klager,
    val sakenGjelder: SakenGjelder? = null,
    val ytelse: Ytelse,
    val type: Type,
    val kildeReferanse: String,
    val dvhReferanse: String?,
    val fagsystem: Fagsystem,
    val fagsakId: String,
    val sakMottattKlageinstans: LocalDateTime,
    val saksdokumenter: MutableSet<Saksdokument>,
    val innsendingsHjemler: Set<Hjemmel>?,
    val sendtTilTrygderetten: LocalDateTime,
    val registreringsHjemmelSet: Set<Registreringshjemmel>? = null,
    val ankebehandlingUtfall: ExternalUtfall,
    val previousSaksbehandlerident: String?,
)

fun Behandling.createAnkeITrygderettenbehandlingInput(): AnkeITrygderettenbehandlingInput {
    return AnkeITrygderettenbehandlingInput(
        klager = klager,
        sakenGjelder = sakenGjelder,
        ytelse = ytelse,
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = kildeReferanse,
        dvhReferanse = dvhReferanse,
        fagsystem = fagsystem,
        fagsakId = fagsakId,
        sakMottattKlageinstans = mottattKlageinstans,
        saksdokumenter = saksdokumenter,
        innsendingsHjemler = hjemler,
        sendtTilTrygderetten = avsluttetAvSaksbehandler!!,
        registreringsHjemmelSet = registreringshjemler,
        ankebehandlingUtfall = ExternalUtfall.valueOf(utfall!!.name),
        previousSaksbehandlerident = tildeling!!.saksbehandlerident
    )
}