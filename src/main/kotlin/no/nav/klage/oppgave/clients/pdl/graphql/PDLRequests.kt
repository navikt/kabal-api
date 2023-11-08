package no.nav.klage.oppgave.clients.pdl.graphql

data class PersonGraphqlQuery(
    val query: String,
    val variables: FnrVariables
)

data class FnrVariables(
    val ident: String
)

fun hentPersonQuery(fnr: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").cleanForGraphql()
    return PersonGraphqlQuery(query, FnrVariables(fnr))
}

fun hentIdenterQuery(fnr: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentIdenter.graphql").cleanForGraphql()
    return PersonGraphqlQuery(query, FnrVariables(fnr))
}



