package no.nav.klage.oppgave.clients.oppgaveapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveApiRecord(
    val id: Long,
    val versjon: Int,
    val journalpostId: String?,
    val saksreferanse: String?,
    val mappeId: Long?,
    val status: Status?,
    val tildeltEnhetsnr: String?,
    val opprettetAvEnhetsnr: String?,
    val endretAvEnhetsnr: String?,
    val tema: String,
    val temagruppe: String?,
    val behandlingstema: String?,
    val oppgavetype: String?,
    val behandlingstype: String?,
    val prioritet: Prioritet?,
    val tilordnetRessurs: String?,
    val beskrivelse: String?,
    val fristFerdigstillelse: LocalDate?,
    val aktivDato: String?,
    val opprettetAv: String?,
    val endretAv: String?,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val opprettetTidspunkt: LocalDateTime,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val endretTidspunkt: LocalDateTime?,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val ferdigstiltTidspunkt: LocalDateTime?,
    val behandlesAvApplikasjon: String?,
    val journalpostkilde: String?,
    val identer: List<Ident>?,
    val metadata: Map<String, String>?,
    val bnr: String?,
    val samhandlernr: String?,
    val aktoerId: String?,
    val orgnr: String?,
)

enum class Status(val statusId: Long) {

    OPPRETTET(1),
    AAPNET(2),
    UNDER_BEHANDLING(3),
    FERDIGSTILT(4),
    FEILREGISTRERT(5);

    companion object {

        fun of(statusId: Long): Status {
            return entries.firstOrNull { it.statusId == statusId }
                ?: throw IllegalArgumentException("No status with $statusId exists")
        }

        fun kategoriForStatus(status: Status): Statuskategori {
            return when (status) {
                AAPNET, OPPRETTET, UNDER_BEHANDLING -> Statuskategori.AAPEN
                FEILREGISTRERT, FERDIGSTILT -> Statuskategori.AVSLUTTET
            }
        }
    }

    fun kategoriForStatus(): Statuskategori {
        return kategoriForStatus(this)
    }
}

enum class Prioritet {
    HOY,
    NORM,
    LAV
}

data class Ident(
    val id: Long?,
    val identType: IdentType,
    val verdi: String,
    val folkeregisterident: String?,
    val registrertDato: LocalDate?
)

enum class IdentType {
    AKTOERID, ORGNR, SAMHANDLERNR, BNR
}

enum class Statuskategori {
    AAPEN,
    AVSLUTTET;

    fun statuserForKategori(kategori: Statuskategori): List<Status> {
        return when (kategori) {
            AAPEN -> aapen()
            AVSLUTTET -> avsluttet()
        }
    }

    fun avsluttet(): List<Status> {
        return listOf(Status.FERDIGSTILT, Status.FEILREGISTRERT)
    }

    fun aapen(): List<Status> {
        return listOf(Status.OPPRETTET, Status.AAPNET, Status.UNDER_BEHANDLING)
    }
}

class OppgaveClientException : Exception {
    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

data class OppgaveMapperResponse(
    val antallTreffTotalt: Int,
    val mapper: List<OppgaveMappe>
) {
    data class OppgaveMappe(
        val id: Int?,
        val enhetsnr: String,
        val navn: String,
        val tema: String?,
        val versjon: Int,
        val opprettetAv: String?,
        val endretAv: String?,
        @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
        val opprettetTidspunkt: LocalDateTime?,
        @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
        val endretTidspunkt: LocalDateTime?
    )
}