package no.nav.klage.oppgave.clients.klagelookup

data class UserResponse (
    val navIdent: String,
    val sammensattNavn: String,
    val fornavn: String,
    val etternavn: String,
)

data class ExtendedUserResponse (
    val navIdent: String,
    val sammensattNavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val enhet: Enhet,
)

data class Enhet (
    val enhetNr: String,
    val enhetNavn: String,
)

data class GroupMembershipsResponse (
    val groupIds: List<String>,
)