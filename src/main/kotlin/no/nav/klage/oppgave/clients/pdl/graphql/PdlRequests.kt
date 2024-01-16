package no.nav.klage.oppgave.clients.pdl.graphql

data class PersonGraphqlQuery(
    val query: String,
    val variables: IdentVariables
)

data class IdentVariables(
    val ident: String
)

fun hentPersonQuery(ident: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").cleanForGraphql()
    return PersonGraphqlQuery(query, IdentVariables(ident))
}

fun hentFolkeregisterIdentQuery(ident: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentIdenter.graphql").cleanForGraphql()
    return PersonGraphqlQuery(query, IdentVariables(ident))
}
