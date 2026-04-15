package no.nav.klage.oppgave.clients.nom

import java.net.URL

data class AnsattGraphqlQuery(
    val query: String,
    val variables: IdentVariables
)

data class IdentVariables(
    val navident: String
)

data class AnsatteGraphqlQuery(
    val query: String,
    val variables: GetAnsatteIdentVariables
)

data class GetAnsatteIdentVariables(
    val navidenter: List<String>
)

fun getAnsatteQuery(navIdenter: List<String>): AnsatteGraphqlQuery {
    val query =
        AnsatteGraphqlQuery::class.java.getResource("/nom/getAnsatte.graphql").cleanForGraphql()
    return AnsatteGraphqlQuery(query, GetAnsatteIdentVariables(navIdenter))
}

fun getAnsattQuery(navIdent: String): AnsattGraphqlQuery {
    val query =
        AnsattGraphqlQuery::class.java.getResource("/nom/getAnsatt.graphql").cleanForGraphql()
    return AnsattGraphqlQuery(query, IdentVariables(navIdent))
}

fun URL.cleanForGraphql() = readText().replace("[\n\r]", "")