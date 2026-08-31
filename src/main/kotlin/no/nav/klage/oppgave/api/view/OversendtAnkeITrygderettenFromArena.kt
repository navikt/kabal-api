package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import java.time.LocalDate
import java.util.UUID

@Schema
data class OversendtAnkeITrygderettenFromArena(
    @Schema(
        description = "Fnr for saken gjelder. Brukes også som klager.",
        required = true,
    )
    val sakenGjelder: String,
    @Schema(
        description = "Ytelse",
        required = true,
    )
    val ytelseId: String,
    @Schema(
        description = "FagsakId fra Arena.",
        required = true,
    )
    val fagsakId: String,
    @Schema(
        description = "Tidspunkt for når sak ble mottatt i klageinstans.",
        required = true,
    )
    val sakMottattKlageinstans: String,
    @Schema(
        description = "Tidspunkt for når saken ble sendt til Trygderetten.",
        required = true,
    )
    val sendtTilTrygderetten: String,
    @Schema(
        description = "Hjemler knyttet til anken.",
        required = true,
    )
    val hjemmelIdList: List<String>,
    @Schema(
        description = "Gosys-oppgave id.",
        required = true,
    )
    val gosysOppgaveId: Long,
)

fun OversendtAnkeITrygderettenFromArena.toAnkeITrygderettenbehandlingInput(): AnkeITrygderettenbehandlingInput {
    val partId = PartId(type = PartIdType.PERSON, value = sakenGjelder)
    val partUuid = UUID.randomUUID()

    return AnkeITrygderettenbehandlingInput(
        klager = Klager(id = partUuid, partId = partId),
        sakenGjelder = SakenGjelder(id = partUuid, partId = partId),
        prosessfullmektig = null,
        ytelse = Ytelse.of(ytelseId),
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = fagsakId,
        dvhReferanse = null,
        fagsystem = Fagsystem.AO01,
        fagsakId = fagsakId,
        sakMottattKlageinstans = LocalDate.parse(sakMottattKlageinstans).atStartOfDay(),
        saksdokumenter = mutableSetOf(),
        innsendingsHjemler = hjemmelIdList.map { Hjemmel.of(it) }.toSet(),
        sendtTilTrygderetten = LocalDate.parse(sendtTilTrygderetten).atStartOfDay(),
        paaanketVedtaksdato = null,
        forsterketRett = false,
        registreringsHjemmelSet = null,
        ankebehandlingUtfall = null,
        previousSaksbehandlerident = null,
        gosysOppgaveId = gosysOppgaveId,
        tilbakekreving = false,
        gosysOppgaveRequired = true,
        initiatingSystem = Behandling.InitiatingSystem.FAGSYSTEM,
        previousBehandlingId = null,
    )
}
