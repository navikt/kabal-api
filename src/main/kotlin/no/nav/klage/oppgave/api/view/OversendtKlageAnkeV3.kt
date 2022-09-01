package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.klage.Mottak
import no.nav.klage.oppgave.domain.klage.MottakHjemmel
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.validation.constraints.PastOrPresent

@Schema
data class OversendtKlageAnkeV3(
    @Schema(
        required = true,
        example = "KLAGE",
        description = "Gyldige verdier er KLAGE og ANKE"
    )
    val type: Type,
    @Schema(
        required = true
    )
    val klager: OversendtKlager,
    @Schema(
        description = "Kan settes dersom klagen gjelder en annen enn den som har levert klagen",
        required = false
    )
    val sakenGjelder: OversendtSakenGjelder? = null,
    @Schema(
        description = "Fagsak brukt til journalføring. Dersom denne er tom journalfører vi på generell sak",
        required = false
    )
    val fagsak: OversendtSak? = null,
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
        description = "Url tilbake til kildesystem for innsyn i sak",
        required = false,
        example = "https://k9-sak.adeo.no/behandling/12345678"
    )
    val innsynUrl: String? = null,
    @Schema(
        description = "Hjemler knyttet til klagen",
        required = false
    )
    val hjemler: List<Hjemmel>? = emptyList(),
    @Schema(
        description = "ID på enheten som behandlet vedtaket som denne henvendelsen gjelder.",
        required = true
    )
    val forrigeBehandlendeEnhet: String,
    @Schema(
        description = "Liste med relevante journalposter til klagen. Listen kan være tom.",
        required = true
    )
    val tilknyttedeJournalposter: List<OversendtDokumentReferanse> = emptyList(),
    @field:PastOrPresent(message = "Dato for mottatt Nav må være i fortiden eller i dag")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val brukersHenvendelseMottattNavDato: LocalDate,
    val innsendtTilNav: LocalDate,
    @Schema(
        description = "Kan settes dersom førsteinstans ønsker å overstyre frist. Ikke i bruk per i dag.",
        required = false
    )
    val frist: LocalDate? = null,
    @Schema(
        description = "Deprecated. Bruk sakMottattKaTidspunkt i stedet. Kan settes for å overstyre når KA mottok klage/anke.",
        required = false,
        example = "2020-12-20"
    )
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val sakMottattKaDato: LocalDate? = null,
    @Schema(
        description = "Kan settes for å overstyre når KA mottok klage/anke.",
        required = false,
        example = "2020-12-20T00:00"
    )
    val sakMottattKaTidspunkt: LocalDateTime? = null,
    @Schema(
        description = "Legges ved melding ut fra KA på Kafka, brukes for filtrering",
        required = true,
        example = "K9"
    )
    val kilde: KildeFagsystem,
    @Schema(
        example = "OMS_OMP",
        description = "Ytelse",
        required = true
    )
    val ytelse: Ytelse,
    @Schema(
        description = "Kommentarer fra saksbehandler i førsteinstans som ikke er med i oversendelsesbrevet klager mottar",
        required = false
    )
    val kommentar: String? = null

)

fun OversendtKlageAnkeV3.toMottak(forrigeBehandlingId: UUID? = null) = Mottak(
    type = type,
    klager = klager.toKlagepart(),
    sakenGjelder = sakenGjelder?.toSakenGjelder(),
    innsynUrl = innsynUrl,
    sakFagsystem = kilde.mapFagsystem(),
    sakFagsakId = fagsak?.fagsakId,
    kildeReferanse = kildeReferanse,
    dvhReferanse = dvhReferanse,
    hjemler = hjemler?.map { MottakHjemmel(hjemmelId = it.id) }?.toSet(),
    forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
    mottakDokument = tilknyttedeJournalposter.map { it.toMottakDokument() }.toMutableSet(),
    innsendtDato = innsendtTilNav,
    brukersHenvendelseMottattNavDato = brukersHenvendelseMottattNavDato,
    sakMottattKaDato = when {
        sakMottattKaTidspunkt != null -> sakMottattKaTidspunkt
        sakMottattKaDato != null -> sakMottattKaDato.atStartOfDay()
        else -> LocalDateTime.now()
    },
    fristFraFoersteinstans = frist,
    ytelse = ytelse,
    forrigeBehandlingId = forrigeBehandlingId
)