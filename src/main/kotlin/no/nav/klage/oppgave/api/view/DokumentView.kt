package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class DokumenterResponse(
    val dokumenter: List<DokumentReferanse>,
    val pageReference: String? = null,
    val antall: Int,
    val totaltAntall: Int,
    val sakList: List<DokumentReferanse.Sak>,
    val avsenderMottakerList: List<DokumentReferanse.AvsenderMottaker>,
    val temaIdList: List<String>,
    val journalposttypeList: List<DokumentReferanse.Journalposttype>,
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
)

data class DokumentReferanse(
    val journalpostId: String,
    val dokumentInfoId: String,
    val tittel: String?,
    val tema: String?,
    val temaId: String?,
    val harTilgangTilArkivvariant: Boolean,
    val valgt: Boolean,
    val logiskeVedlegg: List<LogiskVedlegg>?,
    val vedlegg: MutableList<VedleggReferanse> = mutableListOf(),
    val journalposttype: Journalposttype?,
    val journalstatus: Journalstatus?,
    val sak: Sak?,
    val avsenderMottaker: AvsenderMottaker?,
    val opprettetAvNavn: String?,
    val datoOpprettet: LocalDateTime,
    val datoRegSendt: LocalDateTime?,
    val relevanteDatoer: List<RelevantDatoOld>,
    val timeline: List<TimelineItem>,
    val kanal: String,
    val kanalnavn: String,
    val utsendingsinfo: Utsendingsinfo?,
    val originalJournalpostId: String?,
    val sortKey: String,
) {

    enum class Journalstatus {
        //Journalposten er mottatt, men ikke journalført. "Mottatt" er et annet ord for "arkivert" eller "midlertidig journalført"
        //Statusen vil kun forekomme for inngående dokumenter.
        MOTTATT,

        //Journalposten er ferdigstilt og ansvaret for videre behandling av forsendelsen er overført til fagsystemet. Journalen er i prinsippet låst for videre endringer.
        //Journalposter med status JOURNALFØRT oppfyller minimumskrav til metadata i arkivet, som for eksempel tema, sak, bruker og avsender.
        JOURNALFOERT,

        //Journalposten med tilhørende dokumenter er ferdigstilt, og journalen er i prinsippet låst for videre endringer. FERDIGSTILT tilsvarer statusen JOURNALFØRT for inngående dokumenter.
        //Tilsvarer begrepet Arkivert
        //Statusen kan forekomme for utgående dokumenter og notater.
        FERDIGSTILT,

        //Dokumentet er sendt til bruker. Statusen benyttes også når dokumentet er tilgjengeliggjort for bruker på DittNAV, og bruker er varslet.
        //Tilsvarer begrepet Sendt
        //Statusen kan forekomme for utgående dokumenter.
        EKSPEDERT,

        //Journalposten er opprettet i arkivet, men fremdeles under arbeid.
        //Statusen kan forekomme for utgående dokumenter og notater.
        UNDER_ARBEID,

        //Journalposten har blitt arkivavgrenset etter at den feilaktig har blitt knyttet til en sak.
        //Statusen kan forekomme for alle journalposttyper.
        FEILREGISTRERT,

        //Journalposten er arkivavgrenset grunnet en feilsituasjon, ofte knyttet til skanning eller journalføring.
        //Statusen vil kun forekomme for inngående dokumenter.
        UTGAAR,

        //Utgående dokumenter og notater kan avbrytes mens de er under arbeid, og ikke enda er ferdigstilt. Statusen AVBRUTT brukes stort sett ved feilsituasjoner knyttet til dokumentproduksjon.
        //Statusen kan forekomme for utgående dokumenter og notater.
        AVBRUTT,

        //Journalposten har ikke noen kjent bruker.
        //NB: UKJENT_BRUKER er ikke en midlertidig status, men benyttes der det ikke er mulig å journalføre fordi man ikke klarer å identifisere brukeren forsendelsen gjelder.
        //Statusen kan kun forekomme for inngående dokumenter.
        UKJENT_BRUKER,

        //Statusen benyttes bl.a. i forbindelse med brevproduksjon for å reservere 'plass' i journalen for dokumenter som skal populeres på et senere tidspunkt.
        //Dersom en journalpost blir stående i status RESEVERT over tid, tyder dette på at noe har gått feil under dokumentproduksjon eller ved skanning av et utgående dokument.
        //Statusen kan forekomme for utgående dokumenter og notater.
        RESERVERT,

        //Midlertidig status på vei mot MOTTATT.
        //Dersom en journalpost blir stående i status OPPLASTING_DOKUMENT over tid, tyder dette på at noe har gått feil under opplasting av vedlegg ved arkivering.
        //Statusen kan kun forekomme for inngående dokumenter.
        OPPLASTING_DOKUMENT,

        //Dersom statusfeltet i Joark er tomt, mappes dette til "UKJENT"
        UKJENT
    }

    data class AvsenderMottaker(
        val id: String,
        val type: AvsenderMottakerIdType,
        val navn: String?,
    ) {
        enum class AvsenderMottakerIdType {
            //TODO look into NULL
            FNR, ORGNR, HPRNR, UTL_ORG, UKJENT, NULL
        }
    }

    data class VedleggReferanse(
        val dokumentInfoId: String,
        val tittel: String?,
        val harTilgangTilArkivvariant: Boolean,
        val valgt: Boolean,
        val originalJournalpostId: String?,
        val sortKey: String,
        val logiskeVedlegg: List<LogiskVedlegg>?,
    )

    enum class Journalposttype {
        I, //Inngående dokument: Dokumentasjon som NAV har mottatt fra en ekstern part. De fleste inngående dokumenter er søknader, ettersendelser av dokumentasjon til sak, eller innsendinger fra arbeidsgivere. Meldinger brukere har sendt til "Skriv til NAV" arkiveres også som inngående dokumenter.
        U, //Utgående dokument: Dokumentasjon som NAV har produsert og sendt ut til en ekstern part. De fleste utgående dokumenter er informasjons- eller vedtaksbrev til privatpersoner eller organisasjoner. "Skriv til NAV"-meldinger som saksbehandlere har sendt til brukere arkiveres også som utgående dokumenter.
        N //Notat: Dokumentasjon som NAV har produsert selv, uten at formålet er å distribuere dette ut av NAV. Eksempler på notater er samtalereferater med veileder på kontaktsenter og interne forvaltningsnotater.
    }

    data class Sak(
        val datoOpprettet: LocalDateTime?,
        val fagsakId: String?,
        val fagsaksystem: String?,
        val fagsystemId: String?,
    )

    data class TimelineItem(
        val timestamp: LocalDateTime,
        val type: TimelineType,
    ) {
        enum class TimelineType {
            OPPRETTET,
            SENDT_PRINT,
            EKSPEDERT,
            JOURNALFOERT,
            REGISTRERT,
            AVSENDER_RETUR,
            LEST,
        }
    }

    data class RelevantDatoOld(
        val dato: LocalDateTime,
        val datotype: DatotypeOld,
    ) {
        enum class DatotypeOld {
            DATO_SENDT_PRINT,
            DATO_EKSPEDERT,
            DATO_JOURNALFOERT,
            DATO_REGISTRERT,
            DATO_AVS_RETUR,
            DATO_DOKUMENT,
            DATO_LEST,
        }
    }

    data class Utsendingsinfo(
        val epostVarselSendt: EpostVarselSendt?,
        val smsVarselSendt: SmsVarselSendt?,
        val fysiskpostSendt: FysiskpostSendt?,
        val digitalpostSendt: DigitalpostSendt?,
    ) {
        data class EpostVarselSendt(
            val tittel: String,
            val adresse: String,
            val varslingstekst: String,
        )

        data class SmsVarselSendt(
            val adresse: String,
            val varslingstekst: String,
        )

        data class FysiskpostSendt(
            val adressetekstKonvolutt: String,
        )

        data class DigitalpostSendt(
            val adresse: String,
        )
    }

}

data class TilknyttetDokument(val journalpostId: String, val dokumentInfoId: String)

data class JournalfoertDokumentMetadata(
    val journalpostId: String,
    val dokumentInfoId: String,
    val title: String,
    val harTilgangTilArkivvariant: Boolean,
)

data class DokumentUnderArbeidMetadata(
    val behandlingId: UUID,
    val documentId: UUID,
    val title: String,
)

data class MergedDocumentsMetadata(
    val mergedDocumentId: UUID,
    val title: String,
    val archivedDocuments: List<JournalfoertDokument>,
) {
    data class JournalfoertDokument(
        val journalpostId: String,
        val dokumentInfoId: String,
    )
}

data class LogiskVedlegg(
    val tittel: String,
    val logiskVedleggId: String,
)

data class LogiskVedleggInput(
    val tittel: String,
)

data class LogiskVedleggResponse(
    val tittel: String,
    val logiskVedleggId: String,
)