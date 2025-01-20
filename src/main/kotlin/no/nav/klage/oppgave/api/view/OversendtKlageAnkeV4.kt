package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Schema
data class OversendtKlageAnkeV4(
    @Schema(
        required = true,
        example = "KLAGE",
    )
    val type: OversendtType,

    @Schema(
        required = true
    )
    val sakenGjelder: OversendtPart,

    @Schema(
        description = "Kan settes dersom klagen er levert av annen enn den saken gjelder.",
        required = false
    )
    val klager: OversendtPart?,

    @Schema(
        description = "Prosessfullmektig i saken. Brukes bl.a. til automatisk svarbrev.",
        required = false
    )
    val prosessfullmektig: OversendtProsessfullmektig?,

    @Schema(
        description = "Fagsak brukt til journalføring.",
        required = true
    )
    val fagsak: OversendtSak,

    @Schema(
        description = "Teknisk id brukt i avsendersystemet som Kabal vil bruke når vi kommuniserer tilbake.",
        required = true
    )
    val kildeReferanse: String,

    @Schema(
        description = "Id som rapporteres på til DVH. Kabal bruker kildeReferanse hvis denne ikke er satt.",
        required = false
    )
    val dvhReferanse: String?,

    @Schema(
        description = "Hjemler knyttet til klagen.",
        required = true
    )
    val hjemler: List<Hjemmel>,

    @Schema(
        description = "Id på forrige enhet som fattet vedtak i saken. For klager er dette typisk en vedtaksenhet, men for anker er det typisk en klageenhet.",
        required = true
    )
    val forrigeBehandlendeEnhet: String,

    @Schema(
        description = "Liste med relevante journalposter til klagen. Listen kan være tom.",
        required = true
    )
    val tilknyttedeJournalposter: List<OversendtDokumentReferanse>,

    @Schema(
        description = "Dato for når klagen ble mottatt i vedtaksinstansen. Skal ikke brukes for anker.",
        required = false
    )
    val brukersKlageMottattVedtaksinstans: LocalDate?,

    @Schema(
        description = "Kan settes for å overstyre fristen i Kabal. Uten oppgitt frist settes denne til 12 uker etter sakMottattKaTidspunkt.",
        required = false
    )
    val frist: LocalDate?,

    @Schema(
        description = "Kan settes for å overstyre når KA mottok klage/anke. Settes ellers til now().",
        required = false,
        example = "2020-12-20T00:00"
    )
    val sakMottattKaTidspunkt: LocalDateTime?,

    @Schema(
        example = "OMS_OMP",
        description = "Sakens ytelse. Bruker KA sitt kodeverk.",
        required = true
    )
    val ytelse: Ytelse,

    @Schema(
        description = "Kommentarer fra saksbehandler i førsteinstans som ikke er med i oversendelsesbrevet klager mottar.",
        required = false
    )
    val kommentar: String?,

    @Schema(
        description = "Brukes for å hindre Kabal i å sende ut automatisk svarbrev.",
        required = false
    )
    val hindreAutomatiskSvarbrev: Boolean?,

    @Schema(
        description = "Brukes for å tildele saken i Kabal. Må være en saksbehandler som allerede fins i Kabal.",
        required = false
    )
    val saksbehandlerIdentForTildeling: String?,

    )

fun OversendtKlageAnkeV4.toMottak(forrigeBehandlingId: UUID? = null) = Mottak(
    type = Type.valueOf(type.name),
    klager = klager?.toKlager() ?: sakenGjelder.toKlager(),
    sakenGjelder = sakenGjelder.toSakenGjelder(),
    fagsystem = fagsak.fagsystem,
    fagsakId = fagsak.fagsakId,
    kildeReferanse = kildeReferanse,
    dvhReferanse = dvhReferanse,
    hjemler = hjemler.map { MottakHjemmel(hjemmelId = it.id) }.toSet(),
    forrigeBehandlendeEnhet = forrigeBehandlendeEnhet,
    mottakDokument = tilknyttedeJournalposter.map { it.toMottakDokument() }.toMutableSet(),
    brukersKlageMottattVedtaksinstans = brukersKlageMottattVedtaksinstans,
    sakMottattKaDato = sakMottattKaTidspunkt ?: LocalDateTime.now(),
    frist = frist,
    ytelse = ytelse,
    forrigeBehandlingId = forrigeBehandlingId,
    kommentar = kommentar,
    prosessfullmektig = prosessfullmektig?.toProsessfullmektig(),
    forrigeSaksbehandlerident = null,
    sentFrom = Mottak.Sender.FAGSYSTEM,
)

enum class OversendtType {
    KLAGE,
    ANKE
}

@Schema(
    description = "Kan settes dersom klager har en prosessfullmektig",
    required = false
)
data class OversendtProsessfullmektig(
    @Schema(
        required = false
    )
    val id: OversendtPartId?,
    @Schema(
        required = false
    )
    val navn: String?,
    @Schema(
        required = false
    )
    val adresse: OversendtAdresse?,
) {
    fun toProsessfullmektig() = Prosessfullmektig(
        partId = id?.toPartId(),
        address = adresse?.let {
            Adresse(
                adresselinje1 = it.adresselinje1,
                adresselinje2 = it.adresselinje2,
                adresselinje3 = it.adresselinje3,
                postnummer = it.postnummer,
                poststed = it.poststed,
                landkode = it.land,
            )
        },
        navn = navn,
    )
}

data class OversendtAdresse(
    @Schema(
        required = false
    )
    val adresselinje1: String?,
    @Schema(
        required = false
    )
    val adresselinje2: String?,
    @Schema(
        required = false
    )
    val adresselinje3: String?,
    @Schema(
        required = false
    )
    val postnummer: String?,
    @Schema(
        required = false
    )
    val poststed: String?,
    @Schema(
        required = true,
        description = "ISO 3166-1 alpha-2 kode. F.eks. NO for Norge."
    )
    val land: String,
) {
    fun validateAddress() {
        if (land == "NO") {
            if (postnummer == null) {
                throw OversendtKlageNotValidException("Trenger postnummer for norske adresser.")
            }
        } else if (adresselinje1 == null) {
            throw OversendtKlageNotValidException("Trenger adresselinje1 for utenlandske adresser.")
        }
    }
}

data class OversendtPart(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
) {
    fun toSakenGjelder() = SakenGjelder(
        partId = id.toPartId(),
    )

    fun toKlager() = Klager(
        partId = id.toPartId(),
    )
}