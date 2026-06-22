package no.nav.klage.oppgave.clients.gosysoppgave

import java.time.LocalDate

abstract class UpdateOppgaveRequest

data class TildelGosysOppgaveRequestWithoutRepresenterer(
    val meta: PatchMeta,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequest()

data class TildelGosysOppgaveRequestWithRepresenterer(
    val meta: PatchMetaWithRepresenterer,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequest()

data class FradelGosysOppgaveRequestWithoutRepresenterer(
    val meta: PatchMeta,
    val fordeling: FordelingFradelingRequest,
) : UpdateOppgaveRequest()

data class FradelGosysOppgaveRequestWithRepresenterer(
    val meta: PatchMetaWithRepresenterer,
    val fordeling: FordelingFradelingRequest,
) : UpdateOppgaveRequest()

data class AddKommentarToGosysOppgaveRequestWithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
) : UpdateOppgaveRequest()

data class AddKommentarToGosysOppgaveRequestWithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
) : UpdateOppgaveRequest()

data class UpdateFristInGosysOppgaveRequestWithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val fristDato: LocalDate,
) : UpdateOppgaveRequest()

data class UpdateFristInGosysOppgaveRequestWithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val fristDato: LocalDate,
) : UpdateOppgaveRequest()

data class AvsluttGosysOppgaveRequestWithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val status: StatusV2,
) : UpdateOppgaveRequest()

data class AvsluttGosysOppgaveRequestWithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val status: StatusV2,
) : UpdateOppgaveRequest()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestWithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequest()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithoutRepresenterer(
    val meta: PatchMetaWithKommentar,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
    val nokkelord: Set<String>,
) : UpdateOppgaveRequest()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestWithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
) : UpdateOppgaveRequest()

data class UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithRepresenterer(
    val meta: PatchMetaWithKommentarAndRepresenterer,
    val fristDato: LocalDate,
    val fordeling: FordelingTildelingRequest,
    val nokkelord: Set<String>,
) : UpdateOppgaveRequest()

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
    val medarbeider: Medarbeider? = null,
)

data class MappeRequestDto(
    val id: Long,
)
