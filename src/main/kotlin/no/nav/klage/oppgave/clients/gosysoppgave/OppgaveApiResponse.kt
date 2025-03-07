package no.nav.klage.oppgave.clients.gosysoppgave

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class GosysOppgaveRecord(
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

data class OppgaveListResponse(
    val antallTreffTotalt: Int,
    val oppgaver: List<GosysOppgaveRecord>
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