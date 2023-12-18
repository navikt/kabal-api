package no.nav.klage.oppgave.clients.nom

import java.net.URL

data class AnsattGraphqlQuery(
    val query: String,
    val variables: IdentVariables
)

data class IdentVariables(
    val navident: String
)

fun getAnsattQuery(navIdent: String): AnsattGraphqlQuery {
    val query =
        AnsattGraphqlQuery::class.java.getResource("/nom/getAnsatt.graphql").cleanForGraphql()
    return AnsattGraphqlQuery(query, IdentVariables(navIdent))
}

fun URL.cleanForGraphql() = readText().replace("[\n\r]", "")