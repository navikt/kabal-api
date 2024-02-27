package no.nav.klage.oppgave.clients.kabaldocument.model.request

import java.util.*

data class DokumentEnhetWithDokumentreferanserInput(
    val avsenderMottakerList: List<AvsenderMottakerInput>,
    val journalfoeringData: JournalfoeringDataInput,
    val dokumentreferanser: DokumentInput,
    val dokumentTypeId: String,
    val journalfoerendeSaksbehandlerIdent: String,
) {
    data class DokumentInput(
        val hoveddokument: Dokument,
        val vedlegg: List<Dokument>?,
        val journalfoerteVedlegg: List<JournalfoertDokument>?,
    ) {
        data class Dokument(
            val mellomlagerId: String,
            val name: String,
            val sourceReference: UUID?,
        )

        data class JournalfoertDokument(
            val kildeJournalpostId: String,
            val dokumentInfoId: String,
        )
    }
}

data class AvsenderMottakerInput(
    val partId: PartIdInput,
    val navn: String,
    //Trenger ikke denne når kanal tas i bruk
    val localPrint: Boolean,
    val tvingSentralPrint: Boolean,
    val adresse: Address?,
    val kanal: Kanal?,
) {
    data class Address(
        val adressetype: Adressetype,
        val adresselinje1: String?,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val postnummer: String?,
        val poststed: String?,
        val land: String,
    )

    enum class Adressetype {
        NORSK_POSTADRESSE,
        UTENLANDSK_POSTADRESSE,
    }
}
