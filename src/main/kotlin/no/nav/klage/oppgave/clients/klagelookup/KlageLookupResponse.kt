package no.nav.klage.oppgave.clients.klagelookup

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

data class UsersResponse(
    val users: List<UserResponse>,
)

data class UserResponse (
    val navIdent: String,
    val sammensattNavn: String,
    val fornavn: String,
    val etternavn: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtendedUsersResponse(
    val hits: List<ExtendedUserResponse>,
    val misses: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtendedUserResponse (
    val navIdent: String,
    val sammensattNavn: String,
    val fornavn: String,
    val etternavn: String,
    val enhet: Enhet,
)

data class Enhet (
    val enhetNr: String,
    val enhetNavn: String,
)

data class GroupsResponse (
    val groupIds: List<String>,
)

data class FnrResponse(
    val fnr: String,
)

data class AktoerIdResponse(
    val aktoerId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonResponse(
    val foedselsnr: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val sammensattNavn: String,
    val kjoenn: String?,
    val doed: LocalDate?,
    val strengtFortrolig: Boolean,
    val strengtFortroligUtland: Boolean,
    val fortrolig: Boolean,
    val egenAnsatt: Boolean,
    val vergemaalEllerFremtidsfullmakt: Boolean,
    val sikkerhetstiltak: SikkerhetstiltakResponse?,
    val protectedFamilyMembers: List<ProtectedFamilyMemberResponse>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProtectedFamilyMemberResponse(
        val foedselsnr: String,
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val sammensattNavn: String,
        val kjoenn: String?,
        val doed: LocalDate?,
        val strengtFortrolig: Boolean,
        val strengtFortroligUtland: Boolean,
        val fortrolig: Boolean,
        val egenAnsatt: Boolean,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SikkerhetstiltakResponse(
        val tiltakstype: String,
        val beskrivelse: String,
        val gyldigFraOgMed: LocalDate,
        val gyldigTilOgMed: LocalDate,
    )
}