package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import java.time.LocalDateTime

@Schema
data class OversendtAnkeITrygderettenV1(
    @Schema(
        required = true
    )
    val klager: OversendtKlagerLegacy,
    @Schema(
        description = "Kan settes dersom klagen gjelder en annen enn den som har levert klagen",
        required = false
    )
    val sakenGjelder: OversendtSakenGjelder? = null,
    @Schema(
        description = "Fagsak brukt til journalføring. Dersom denne er tom journalfører vi på generell sak",
        required = false
    )
    val fagsak: OversendtSak,
    @Schema(
        description = "Id som er intern for kildesystemet (f.eks. K9) så vedtak fra oss knyttes riktig i kilde",
        required = true
    )
    val kildeReferanse: String,
    @Schema(
        description = "Id som rapporters på til DVH, bruker kildeReferanse hvis denne ikke er satt",
        required = false
    )
    val dvhReferanse: String? = null,
    @Schema(
        description = "Hjemler knyttet til klagen",
        required = false
    )
    val hjemler: Set<Hjemmel>?,
    @Schema(
        description = "Liste med relevante journalposter til klagen. Listen kan være tom.",
        required = true
    )
    val tilknyttedeJournalposter: List<OversendtDokumentReferanse> = emptyList(),
    @Schema(
        description = "Tidspunkt for når KA mottok anken.",
        required = true,
        example = "2020-12-20T00:00"
    )
    val sakMottattKaTidspunkt: LocalDateTime,
    @Schema(
        example = "OMS_OMP",
        description = "Ytelse",
        required = true
    )
    val ytelse: Ytelse,
    @Schema(
        description = "Tidspunkt for når saken ble oversendt til Trygderetten.",
        required = true,
        example = "2020-12-20T00:00"
    )
    val sendtTilTrygderetten: LocalDateTime,
    @Schema(
        description = "Utfall på ankebehandlingen som førte til oversendelse til Trygderetten",
        required = true,
        example = "INNSTILLING_STADFESTELSE"
    )
    val utfall: ExternalUtfall,
)

fun OversendtAnkeITrygderettenV1.createAnkeITrygderettenbehandlingInput(inputDocuments: MutableSet<Saksdokument>): AnkeITrygderettenbehandlingInput {
    val (sakenGjelderPart, klagePart, _) = getParts(sakenGjelder, klager)
    return AnkeITrygderettenbehandlingInput(
        klager = klagePart,
        sakenGjelder = sakenGjelderPart,
        prosessfullmektig = null,
        ytelse = ytelse,
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = kildeReferanse,
        dvhReferanse = dvhReferanse,
        fagsystem = fagsak.fagsystem,
        fagsakId = fagsak.fagsakId,
        sakMottattKlageinstans = sakMottattKaTidspunkt,
        saksdokumenter = inputDocuments,
        innsendingsHjemler = hjemler,
        sendtTilTrygderetten = sendtTilTrygderetten,
        ankebehandlingUtfall = utfall,
        previousSaksbehandlerident = null,
        gosysOppgaveId = null,
        tilbakekreving = false,
        gosysOppgaveRequired = false,
    )
}
