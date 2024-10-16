package no.nav.klage.oppgave.clients.pdl.graphql

data class PersonGraphqlQuery(
    val query: String,
    val variables: IdentVariables
)

data class IdentVariables(
    val ident: String,
    val grupper: Array<IdentType>? = null,
)

fun hentPersonQuery(ident: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").cleanForGraphql()
    return PersonGraphqlQuery(query, IdentVariables(ident))
}

fun hentFolkeregisterIdentQuery(ident: String): PersonGraphqlQuery {
    return hentIdenterQuery(ident = ident, identType = IdentType.FOLKEREGISTERIDENT)
}

fun hentAktorIdQuery(ident: String): PersonGraphqlQuery {
    return hentIdenterQuery(ident = ident, identType = IdentType.AKTORID)
}

private fun hentIdenterQuery(ident: String, identType: IdentType): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentIdenter.graphql").cleanForGraphql()
    return PersonGraphqlQuery(
        query, IdentVariables(
            ident = ident,
            grupper = arrayOf(identType)
        )
    )
}

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
}
