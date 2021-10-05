package no.nav.klage.oppgave.domain.elasticsearch

import org.elasticsearch.index.VersionType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.elasticsearch.annotations.*
import java.time.LocalDate
import java.time.LocalDateTime

@Document(
    indexName = "klagebehandling",
    shards = 3,
    replicas = 2,
    versionType = VersionType.EXTERNAL,
    createIndex = false
)
data class EsKlagebehandling(
    @Id
    val id: String,
    //Må være Long? for å bli Long på JVMen (isf long), og det krever Spring DataES..
    @Version
    val versjon: Long?,

    @Field(type = FieldType.Keyword)
    val klagerFnr: String? = null,
    @Field(type = FieldType.Text)
    val klagerNavn: String? = null,
    @Field(type = FieldType.Text)
    val klagerFornavn: String? = null,
    @Field(type = FieldType.Text)
    val klagerMellomnavn: String? = null,
    @Field(type = FieldType.Text)
    val klagerEtternavn: String? = null,
    @Field(type = FieldType.Keyword)
    val klagerOrgnr: String? = null,
    @Field(type = FieldType.Text)
    val klagerOrgnavn: String? = null,
    @Field(type = FieldType.Keyword)
    val sakenGjelderFnr: String? = null,
    @Field(type = FieldType.Text)
    val sakenGjelderNavn: String? = null,
    @Field(type = FieldType.Text)
    val sakenGjelderFornavn: String? = null,
    @Field(type = FieldType.Text)
    val sakenGjelderMellomnavn: String? = null,
    @Field(type = FieldType.Text)
    val sakenGjelderEtternavn: String? = null,
    @Field(type = FieldType.Keyword)
    val sakenGjelderOrgnr: String? = null,
    @Field(type = FieldType.Text)
    val sakenGjelderOrgnavn: String? = null,

    @Field(type = FieldType.Keyword)
    val tema: String,
    @Field(type = FieldType.Keyword)
    val temaNavn: String,
    @Field(type = FieldType.Keyword)
    val type: String,
    @Field(type = FieldType.Keyword)
    val typeNavn: String,

    @Field(type = FieldType.Keyword)
    val kildeReferanse: String? = null,
    @Field(type = FieldType.Keyword)
    val sakFagsystem: String? = null,
    @Field(type = FieldType.Keyword)
    val sakFagsystemNavn: String? = null,
    @Field(type = FieldType.Keyword)
    val sakFagsakId: String? = null,

    @Field(type = FieldType.Date, format = DateFormat.date)
    val innsendt: LocalDate? = null,
    @Field(type = FieldType.Date, format = DateFormat.date)
    val mottattFoersteinstans: LocalDate? = null,

    @Field(type = FieldType.Keyword)
    val avsenderSaksbehandleridentFoersteinstans: String? = null,
    @MultiField(
        mainField = Field(type = FieldType.Keyword),
        otherFields = [InnerField(type = FieldType.Text, suffix = "text")]
    )
    val avsenderEnhetFoersteinstans: String? = null,

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val mottattKlageinstans: LocalDateTime,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val tildelt: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val foerstTildelt: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val sistTildelt: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val sendtMedunderskriver: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val avsluttet: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val avsluttetAvSaksbehandler: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date)
    val frist: LocalDate?,

    @Field(type = FieldType.Keyword)
    val tildeltSaksbehandlerident: String? = null,
    @Field(type = FieldType.Text)
    val tildeltSaksbehandlernavn: String? = null,
    @Field(type = FieldType.Keyword)
    val medunderskriverident: String? = null,
    @Field(type = FieldType.Keyword)
    val medunderskriverFlyt: String? = null,

    @MultiField(
        mainField = Field(type = FieldType.Keyword),
        otherFields = [InnerField(type = FieldType.Text, suffix = "text")]
    )
    val tildeltEnhet: String?,

    @MultiField(
        mainField = Field(type = FieldType.Keyword),
        otherFields = [InnerField(type = FieldType.Text, suffix = "text")]
    )
    val hjemler: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val hjemlerNavn: List<String> = emptyList(),

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val created: LocalDateTime,

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    var modified: LocalDateTime,

    @Field(type = FieldType.Keyword)
    val kilde: String,

    @Field(type = FieldType.Text)
    val kommentarFraFoersteinstans: String? = null,

    @Field(type = FieldType.Text)
    val internVurdering: String? = null,

    @Field(type = FieldType.Object)
    val vedtak: EsVedtak? = null,

    @Field(type = FieldType.Nested)
    val saksdokumenter: List<EsSaksdokument> = emptyList(),

    @Field(type = FieldType.Keyword)
    val saksdokumenterJournalpostId: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val saksdokumenterJournalpostIdOgDokumentInfoId: List<String> = emptyList(),

    @Field(type = FieldType.Boolean)
    val egenAnsatt: Boolean = false,
    @Field(type = FieldType.Boolean)
    val fortrolig: Boolean = false,
    @Field(type = FieldType.Boolean)
    val strengtFortrolig: Boolean = false,

    /* Enn så lenge har vi bare ett vedtak, og da er det enklere å søke på det når det er flatt her nede enn når det er nested i List<Vedtak>.. */
    @Field(type = FieldType.Keyword)
    val vedtakUtfall: String? = null,
    @Field(type = FieldType.Keyword)
    val vedtakUtfallNavn: String? = null,
    @Field(type = FieldType.Keyword)
    val vedtakGrunn: String? = null,
    @Field(type = FieldType.Keyword)
    val vedtakGrunnNavn: String? = null,
    @MultiField(
        mainField = Field(type = FieldType.Keyword),
        otherFields = [InnerField(type = FieldType.Text, suffix = "text")]
    )
    val vedtakHjemler: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val vedtakHjemlerNavn: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val vedtakBrevmottakerFnr: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val vedtakBrevmottakerOrgnr: List<String> = emptyList(),
    @Field(type = FieldType.Keyword)
    val vedtakJournalpostId: String? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val vedtakCreated: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val vedtakModified: LocalDateTime? = null,
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    val vedtakFerdigstiltIJoark: LocalDateTime? = null,
    @Field(type = FieldType.Keyword)
    val status: Status
) {
    enum class Status {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, FULLFOERT, UKJENT
    }
}
