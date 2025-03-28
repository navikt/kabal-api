package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import java.time.LocalDateTime

data class AnkeITrygderettenbehandlingInput(
    val klager: Klager,
    val sakenGjelder: SakenGjelder? = null,
    val prosessfullmektig: Prosessfullmektig?,
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
    val gosysOppgaveId: Long?,
    val tilbakekreving: Boolean,
)

fun Behandling.createAnkeITrygderettenbehandlingInput(): AnkeITrygderettenbehandlingInput {
    return AnkeITrygderettenbehandlingInput(
        klager = klager,
        sakenGjelder = sakenGjelder,
        prosessfullmektig = prosessfullmektig,
        ytelse = ytelse,
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = kildeReferanse,
        dvhReferanse = dvhReferanse,
        fagsystem = fagsystem,
        fagsakId = fagsakId,
        sakMottattKlageinstans = mottattKlageinstans,
        saksdokumenter = saksdokumenter,
        innsendingsHjemler = hjemler,
        sendtTilTrygderetten = ferdigstilling!!.avsluttetAvSaksbehandler,
        registreringsHjemmelSet = registreringshjemler,
        ankebehandlingUtfall = ExternalUtfall.valueOf(utfall!!.name),
        previousSaksbehandlerident = tildeling!!.saksbehandlerident,
        gosysOppgaveId = gosysOppgaveId,
        tilbakekreving = tilbakekreving,
    )
}