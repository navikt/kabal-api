package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.domain.klage.SattPaaVent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class BehandlingDetaljerView(
    val id: UUID,
    val fraNAVEnhet: String?,
    val fraNAVEnhetNavn: String?,
    val mottattVedtaksinstans: LocalDate? = null,
    val sakenGjelder: SakenGjelderViewWithUtsendingskanal,
    val klager: PartViewWithUtsendingskanal,
    val prosessfullmektig: PartViewWithUtsendingskanal?,
    val temaId: String,
    val ytelseId: String,
    val typeId: String,
    val mottattKlageinstans: LocalDate,
    val tildelt: LocalDate? = null,
    val avsluttetAvSaksbehandlerDate: LocalDate?,
    val isAvsluttetAvSaksbehandler: Boolean,
    val frist: LocalDate? = null,
    val datoSendtMedunderskriver: LocalDate?,
    val hjemmelIdList: List<String>,
    val modified: LocalDateTime,
    val created: LocalDateTime,
    val resultat: VedtakView?,
    val kommentarFraVedtaksinstans: String?,
    val tilknyttedeDokumenter: Set<TilknyttetDokument>,
    val egenAnsatt: Boolean,
    val fortrolig: Boolean,
    val strengtFortrolig: Boolean,
    val vergemaalEllerFremtidsfullmakt: Boolean,
    val dead: LocalDate?,
    val fullmakt: Boolean,
    val kvalitetsvurderingReference: KvalitetsvurderingReference?,
    val sattPaaVent: SattPaaVent? = null,
    val sendtTilTrygderetten: LocalDateTime? = null,
    val kjennelseMottatt: LocalDateTime? = null,
    val feilregistrering: FeilregistreringView? = null,
    val fagsystemId: String,
    val rol: CombinedMedunderskriverAndROLView?,
    val medunderskriver: CombinedMedunderskriverAndROLView,
    val relevantDocumentIdList: Set<String>,
    val saksnummer: String,
    val saksbehandler: SaksbehandlerView?,
    val previousSaksbehandler: SaksbehandlerView?,
) {

    data class CombinedMedunderskriverAndROLView(
        val employee: SaksbehandlerView?,
        val flowState: FlowState,
        val returnedFromROLDate: LocalDate?,
    )

    data class KvalitetsvurderingReference(
        val id: UUID,
        val version: Int,
    )

    data class FeilregistreringView(
        val feilregistrertAv: SaksbehandlerView,
        val registered: LocalDateTime,
        val reason: String,
        val fagsystemId: String,
    )

    interface PartBase {
        val id: String
        val name: String?
        val available: Boolean
        val language: String?
        val statusList: List<PartStatus>
    }

    data class PartStatus(
        val status: Status,
        val date: LocalDate? = null,
    ) {
        enum class Status {
            DEAD,
            DELETED,
            FORTROLIG,
            STRENGT_FORTROLIG,
            EGEN_ANSATT,
            VERGEMAAL,
            FULLMAKT,
            RESERVERT_I_KRR,
            DELT_ANSVAR,
        }
    }

    enum class Sex {
        MANN, KVINNE, UKJENT
    }

    enum class IdType {
        FNR, ORGNR
    }

    interface IdPart {
        val type: IdType
    }

    data class PartView(
        override val id: String,
        override val name: String,
        override val type: IdType,
        override val available: Boolean,
        override val language: String?,
        override val statusList: List<PartStatus>,
        val address: Address?,
    ): PartBase, IdPart

    data class PartViewWithUtsendingskanal(
        override val id: String,
        override val name: String,
        override val type: IdType,
        override val available: Boolean,
        override val language: String?,
        override val statusList: List<PartStatus>,
        val address: Address?,
        val utsendingskanal: Utsendingskanal,
    ): PartBase, IdPart

    data class Address(
        val adresselinje1: String?,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val landkode: String,
        val postnummer: String?,
        val poststed: String?,
    )

    data class SakenGjelderView(
        override val id: String,
        override val name: String,
        override val type: IdType,
        override val available: Boolean,
        override val language: String?,
        override val statusList: List<PartStatus>,
        val sex: Sex,
        val address: Address?,
    ): PartBase, IdPart

    data class SakenGjelderViewWithUtsendingskanal(
        override val id: String,
        override val name: String,
        override val type: IdType,
        override val available: Boolean,
        override val language: String?,
        override val statusList: List<PartStatus>,
        val sex: Sex,
        val address: Address?,
        val utsendingskanal: Utsendingskanal,
    ): PartBase, IdPart

    enum class Utsendingskanal(val navn: String) {
        SENTRAL_UTSKRIFT("Sentral utskrift"),
        SDP("Digital Postkasse Innbygger"),
        NAV_NO("Nav.no"),
        LOKAL_UTSKRIFT("Lokal utskrift"),
        INGEN_DISTRIBUSJON("Ingen distribusjon"),
        TRYGDERETTEN("Trygderetten"),
        DPVT("Taushetsbelagt digital post til virksomhet")
    }
}