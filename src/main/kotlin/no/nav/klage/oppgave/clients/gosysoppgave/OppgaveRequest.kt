package no.nav.klage.oppgave.clients.gosysoppgave

import java.time.LocalDate

abstract class UpdateOppgaveRequest(
    open val versjon: Int,
    open val endretAvEnhetsnr: String?,
)

data class TildelGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Long?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class FradelGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val tilordnetRessurs: String?,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateGosysOppgaveOnCompletedBehandlingRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val fristFerdigstillelse: LocalDate,
    val mappeId: Long?,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class UpdateFristInGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val fristFerdigstillelse: LocalDate,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class Kommentar(
    val tekst: String,
    val automatiskGenerert: Boolean,
)

data class AddKommentarToGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)

data class AvsluttGosysOppgaveRequest(
    override val versjon: Int,
    override val endretAvEnhetsnr: String?,
    val status: Status,
    val kommentar: Kommentar,
) : UpdateOppgaveRequest(versjon = versjon, endretAvEnhetsnr = endretAvEnhetsnr)


//


abstract class UpdateOppgaveRequestV2

data class TildelGosysOppgaveRequestV2WithoutRepresenterer(
    val meta: PatchMeta,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequestV2()

data class TildelGosysOppgaveRequestV2WithRepresenterer(
    val meta: PatchMetaWithRepresenterer,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequestV2()

data class FradelGosysOppgaveRequestV2WithoutRepresenterer(
    val meta: PatchMeta,
    val fordeling: FordelingFradelingRequest,
) : UpdateOppgaveRequestV2()

data class FradelGosysOppgaveRequestV2WithRepresenterer(
    val meta: PatchMetaWithRepresenterer,
    val fordeling: FordelingFradelingRequest,
) : UpdateOppgaveRequestV2()

data class AddKommentarToGosysOppgaveRequestV2WithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
) : UpdateOppgaveRequestV2()

data class AddKommentarToGosysOppgaveRequestV2WithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
) : UpdateOppgaveRequestV2()

data class UpdateFristInGosysOppgaveRequestV2WithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val fristDato: LocalDate,
) : UpdateOppgaveRequestV2()

data class UpdateFristInGosysOppgaveRequestV2WithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val fristDato: LocalDate,
) : UpdateOppgaveRequestV2()

data class AvsluttGosysOppgaveRequestV2WithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val status: StatusV2,
) : UpdateOppgaveRequestV2()

data class AvsluttGosysOppgaveRequestV2WithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val status: StatusV2,
) : UpdateOppgaveRequestV2()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestV2WithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
    val nokkelord: Set<String>,
) : UpdateOppgaveRequestV2()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestV2WithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
    val nokkelord: Set<String>,
) : UpdateOppgaveRequestV2()

//Ikke testet enda

data class PatchMeta(
    val versjon: Int,
)

data class PatchMetaWithKommentar(
    val versjon: Int,
    val kommentar: String,
)

data class PatchMetaWithKommentarAndRepresenterer(
    val versjon: Int,
    val kommentar: String,
    val representerer: Representerer,
)

data class PatchMetaWithoutRepresenterer(
    val versjon: Int,
    val kommentar: String,
)

data class PatchMetaWithRepresenterer(
    val versjon: Int,
    val representerer: Representerer,
)

data class Representerer(
    val enhet: EnhetDto
)

data class EnhetDto(
    val nr: String,
)

data class FordelingTildelingRequest(
    val enhet: EnhetDto,
    val mappe: MappeRequestDto?,
    val medarbeider: Medarbeider?,
)

data class FordelingFradelingRequest(
    val medarbeider: Medarbeider?,
)

data class MappeRequestDto(
    val id: Long,
)

//Jeg begynner med å lage så ekspansive som mulige klasser. Det vil bli en for og en uten
//representerer. Alle kallene kan testes mot apiet.