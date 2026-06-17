package no.nav.klage.oppgave.clients.gosysoppgave


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.annotation.JsonDeserialize

import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class GosysOppgaveRecordV1(
    val id: Long,
    val versjon: Int,
    val journalpostId: String?,
    val mappeId: Long?,
    val status: Status,
    val tildeltEnhetsnr: String,
    val opprettetAvEnhetsnr: String?,
    val endretAvEnhetsnr: String?,
    val tema: String,
    val oppgavetype: String,
    val behandlingstype: String?,
    val beskrivelse: String?,
    val fristFerdigstillelse: LocalDate?,
    val opprettetAv: String?,
    val endretAv: String?,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val opprettetTidspunkt: LocalDateTime?,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val endretTidspunkt: LocalDateTime?,
    @JsonDeserialize(using = OffsetDateTimeToLocalDateTimeDeserializer::class)
    val ferdigstiltTidspunkt: LocalDateTime?,
    val bruker: BrukerDto,
) {
    fun isEditable(): Boolean {
        return status !in listOf(
            Status.FERDIGSTILT,
            Status.FEILREGISTRERT
        )
    }
}

data class BrukerDto(
    val ident: String,
    val type: BrukerType
) {
    enum class BrukerType {
        PERSON,
        ARBEIDSGIVER,
        SAMHANDLER
    }
}

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
    }
}

data class OppgaveMapperResponse(
    val antallTreffTotalt: Int,
    val mapper: List<OppgaveMappe>
) {
    data class OppgaveMappe(
        val id: Long?,
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

data class OppgaveListResponseV1(
    val antallTreffTotalt: Int,
    val oppgaver: List<GosysOppgaveRecordV1>
)

data class Gjelder(
    val behandlingsTema: String?,
    val behandlingstemaTerm: String?,
    val behandlingstype: String?,
    val behandlingstypeTerm: String?,
)

data class OppgavetypeResponse(
    val oppgavetype: String,
    val term: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GosysOppgaveRecordV2(
    val id: Long,
    val kategorisering: KategoriseringDto,
    val fordeling: Fordeling,
    val prioritet: Prioritet,
    val beskrivelse: String?,
    val nokkelord: Set<String>,
    val aktivDato: LocalDate?,
    val fristDato: LocalDate?,
    val versjon: Int,
    val bruker: BrukerDto?,
    val status: StatusV2,
    val opprettet: Historikk,
    val endret: Historikk?,
    val lukket: Historikk?,
    val kommentarer: List<KommentarV2>,
) {
    fun isEditable(): Boolean {
        return status !in listOf(
            StatusV2.FERDIGSTILT,
            StatusV2.FEILREGISTRERT
        )
    }
}

data class KategoriseringDto(
    val tema: Kategorikodeverk,
    val oppgavetype: Kategorikodeverk,
    val behandlingstema: Kategorikodeverk?,
    val behandlingstype: Kategorikodeverk?,
)

data class Kategorikodeverk(
    val kode: String,
    val term: String,
)

data class Fordeling(
    val enhet: Enhet,
    val mappe: MappeDto?,
    val medarbeider: Medarbeider?,
)

data class Enhet(
    val nr: String,
)

data class MappeDto(
    val id: Long,
    val navn: String,
    val tema: String,
)

data class Medarbeider(
    val navident: String,
)

enum class Prioritet {
    LAV, NORMAL, HOY, KRITISK
}

enum class StatusV2 {
    AAPEN, FERDIGSTILT, FEILREGISTRERT
}

data class Historikk(
    val tidspunkt: LocalDateTime,
    val av: UtfortAv?,
)

data class UtfortAv(
    val enhet: Enhet?,
    val medarbeider: Medarbeider?,
    val system: String?,
)

data class KommentarV2(
    val tekst: String?,
    val opprettet: Historikk?,
)