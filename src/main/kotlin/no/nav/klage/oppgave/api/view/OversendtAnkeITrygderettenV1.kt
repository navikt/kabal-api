package no.nav.klage.oppgave.api.view

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.klage.Saksdokument
import java.time.LocalDateTime

@ApiModel
data class OversendtAnkeITrygderettenV1(
    @ApiModelProperty(
        required = true
    )
    val klager: OversendtKlager,
    @ApiModelProperty(
        notes = "Kan settes dersom klagen gjelder en annen enn den som har levert klagen",
        required = false
    )
    val sakenGjelder: OversendtSakenGjelder? = null,
    @ApiModelProperty(
        notes = "Fagsak brukt til journalføring. Dersom denne er tom journalfører vi på generell sak",
        required = false
    )
    val fagsak: OversendtSak,
    @ApiModelProperty(
        notes = "Id som er intern for kildesystemet (f.eks. K9) så vedtak fra oss knyttes riktig i kilde",
        required = true
    )
    val kildeReferanse: String,
    @ApiModelProperty(
        notes = "Id som rapporters på til DVH, bruker kildeReferanse hvis denne ikke er satt",
        required = false
    )
    val dvhReferanse: String? = null,
    @ApiModelProperty(
        notes = "Hjemler knyttet til klagen",
        required = false
    )
    val hjemler: Set<Hjemmel>?,
    @ApiModelProperty(
        notes = "Liste med relevante journalposter til klagen. Listen kan være tom.",
        required = true
    )
    val tilknyttedeJournalposter: List<OversendtDokumentReferanse> = emptyList(),
    @ApiModelProperty(
        notes = "Tidspunkt for når KA mottok anken.",
        required = true,
        example = "2020-12-20T00:00"
    )
    val sakMottattKaTidspunkt: LocalDateTime,
    @ApiModelProperty(
        example = "OMS_OMP",
        notes = "Ytelse",
        required = true
    )
    val ytelse: Ytelse,
    @ApiModelProperty(
        notes = "Tidspunkt for når saken ble oversendt til Trygderetten.",
        required = true,
        example = "2020-12-20T00:00"
    )
    val sendtTilTrygderetten: LocalDateTime,
)

fun OversendtAnkeITrygderettenV1.createAnkeITrygderettenbehandlingInput(inputDocuments: MutableSet<Saksdokument>): AnkeITrygderettenbehandlingInput {
    return AnkeITrygderettenbehandlingInput(
        klager = klager.toKlagepart(),
        sakenGjelder = sakenGjelder?.toSakenGjelder(),
        ytelse = ytelse,
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = kildeReferanse,
        dvhReferanse = dvhReferanse,
        sakFagsystem = fagsak.fagsystem.mapFagsystem(),
        sakFagsakId = fagsak.fagsakId,
        sakMottattKlageinstans = sakMottattKaTidspunkt,
        saksdokumenter = inputDocuments,
        innsendingsHjemler = hjemler,
        sendtTilTrygderetten = sendtTilTrygderetten,
    )
}
