package no.nav.klage.oppgave.api.view

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.klage.Mottak
import no.nav.klage.oppgave.domain.klage.MottakHjemmel
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import javax.validation.constraints.PastOrPresent

@ApiModel
data class OversendtKlageV2(
    @ApiModelProperty(
        required = true,
        example = "KLAGE",
        notes = "Gyldige verdier er KLAGE i både prod og dev"
    )
    val type: Type,
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
    val fagsak: OversendtSak? = null,
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
        notes = "Url tilbake til kildesystem for innsyn i sak",
        required = false,
        example = "https://k9-sak.adeo.no/behandling/12345678"
    )
    val innsynUrl: String? = null,
    @ApiModelProperty(
        notes = "Hjemler knyttet til klagen",
        required = false
    )
    val hjemler: List<Hjemmel>? = emptyList(),
    val avsenderSaksbehandlerIdent: String,
    val avsenderEnhet: String,
    @ApiModelProperty(
        notes = "Liste med relevante journalposter til klagen. Liste kan være tom.",
        required = true
    )
    val tilknyttedeJournalposter: List<OversendtDokumentReferanse> = emptyList(),
    @field:PastOrPresent(message = "Dato for mottatt førsteinstans må være i fortiden eller i dag")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val mottattFoersteinstans: LocalDate,
    val innsendtTilNav: LocalDate,
    @ApiModelProperty(
        notes = "Kan settes dersom førsteinstans ønsker å overstyre frist",
        required = false
    )
    val frist: LocalDate? = null,
    @ApiModelProperty(
        notes = "Kan settes dersom denne saken har blitt sendt til Gosys og derfor har fristen begynt å løpe",
        required = false,
        example = "2020-12-20T00:00"
    )
    val oversendtKaDato: LocalDateTime? = null,
    @ApiModelProperty(
        notes = "Legges ved melding ut fra KA på Kafka, brukes for filtrering",
        required = true,
        example = "K9"
    )
    val kilde: KildeFagsystem,
    @ApiModelProperty(
        example = "OMS_OMP",
        notes = "Ytelse",
        required = true
    )
    val ytelse: Ytelse,
    @ApiModelProperty(
        notes = "Kommentarer fra saksbehandler i førsteinstans som ikke er med i oversendelsesbrevet klager mottar",
        required = false
    )
    val kommentar: String? = null
)

fun OversendtKlageV2.toMottak() = Mottak(
    type = type,
    klager = klager.toKlagepart(),
    sakenGjelder = sakenGjelder?.toSakenGjelder(),
    innsynUrl = innsynUrl,
    sakFagsystem = kilde.mapFagsystem(),
    sakFagsakId = fagsak?.fagsakId,
    kildeReferanse = kildeReferanse,
    dvhReferanse = dvhReferanse,
    hjemler = hjemler?.map { MottakHjemmel(hjemmelId = it.id) }?.toSet(),
    forrigeSaksbehandlerident = avsenderSaksbehandlerIdent,
    forrigeBehandlendeEnhet = avsenderEnhet,
    mottakDokument = tilknyttedeJournalposter.map { it.toMottakDokument() }.toMutableSet(),
    innsendtDato = innsendtTilNav,
    brukersHenvendelseMottattNavDato = mottattFoersteinstans,
    sakMottattKaDato = oversendtKaDato ?: LocalDateTime.now(),
    fristFraFoersteinstans = frist,
    ytelse = ytelse
)