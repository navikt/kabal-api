package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PastOrPresent
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.domain.mottak.MottakHjemmel
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
    val klager: OversendtKlagerLegacy,
    @Schema(
        description = "Kan settes dersom klagen gjelder en annen enn den som har levert klagen",
        required = false
    )
    val sakenGjelder: OversendtSakenGjelder? = null,
    @Schema(
        description = "Fagsak brukt til journalføring.",
        required = false
    )
    val fagsak: OversendtSak,
    @Schema(
        description = "Id som er intern for kildesystemet (f.eks. K9) så vedtak fra oss knyttes riktig i kilde",
        required = true
    )
    val kildeReferanse: String,
    @Schema(
        description = "Id som rapporteres på til DVH, bruker kildeReferanse hvis denne ikke er satt",
        required = false
    )
    val dvhReferanse: String? = null,
    @Deprecated("Ikke i bruk i løsningen")
    @Schema(
        description = "Ikke i bruk",
        required = false,
    )
    val innsynUrl: String? = null,
    @Schema(
        description = "Hjemler knyttet til klagen",
        required = true
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
    @Deprecated("Ikke i bruk i løsningen")
    val innsendtTilNav: LocalDate,
    @Schema(
        description = "Kan settes for å overstyre frist.",
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
    val kilde: Fagsystem,
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
    val kommentar: String? = null,

    @Schema(
        description = "Brukes for å hindre Kabal i å sende ut automatisk svarbrev til bruker.",
        required = false
    )
    val hindreAutomatiskSvarbrev: Boolean?,
    @Schema(
        description = "Brukes for å tildele saken i Kabal. Må være en saksbehandler som allerede fins i Kabal.",
        required = false
    )
    val saksbehandlerIdent: String? = null,

    )

fun OversendtKlageAnkeV3.toMottak(forrigeBehandlingId: UUID? = null): Mottak {
    val (sakenGjelderPart, klagePart, prosessfullmektigPart) = getParts(sakenGjelder, klager)
    return Mottak(
        type = type,
        klager = klagePart,
        sakenGjelder = sakenGjelderPart,
        fagsystem = kilde,
        fagsakId = fagsak.fagsakId,
        kildeReferanse = kildeReferanse,
        dvhReferanse = dvhReferanse,
        hjemler = hjemler!!.map { MottakHjemmel(hjemmelId = it.id) }.toSet(),
        forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
        mottakDokument = tilknyttedeJournalposter.map { it.toMottakDokument() }.toMutableSet(),
        brukersKlageMottattVedtaksinstans = brukersHenvendelseMottattNavDato,
        sakMottattKaDato = when {
            sakMottattKaTidspunkt != null -> sakMottattKaTidspunkt
            sakMottattKaDato != null -> sakMottattKaDato.atStartOfDay()
            else -> LocalDateTime.now()
        },
        frist = frist,
        ytelse = ytelse,
        forrigeBehandlingId = forrigeBehandlingId,
        kommentar = kommentar,
        prosessfullmektig = prosessfullmektigPart,
        forrigeSaksbehandlerident = null,
        sentFrom = Mottak.Sender.FAGSYSTEM,
    )
}