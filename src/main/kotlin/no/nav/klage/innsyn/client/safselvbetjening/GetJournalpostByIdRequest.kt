package no.nav.klage.innsyn.client.safselvbetjening


data class GetJournalpostByIdGraphqlQuery(
    val query: String,
    val variables: GetJournalpostByIdVariables
)

data class GetJournalpostByIdVariables(
    val journalpostId: String
)

fun getJournalpostByIdQuery(journalpostId: String): GetJournalpostByIdGraphqlQuery {
    val query = GraphqlQuery::class.java.getResource("/safselvbetjening/getJournalpostById.graphql").readText()
        .replace("[\n\r]", "")
    return GetJournalpostByIdGraphqlQuery(
        query = query,
        variables = GetJournalpostByIdVariables(journalpostId = journalpostId)
    )
}

data class GraphqlQuery(
    val query: String,
    val variables: Variables
)

data class Variables(
    val ident: String,
    val navnHistorikk: Boolean,
    val grupper: List<IdentGruppe> = listOf(IdentGruppe.AKTORID, IdentGruppe.FOLKEREGISTERIDENT, IdentGruppe.NPID)
)

enum class IdentGruppe {
    FOLKEREGISTERIDENT, NPID, AKTORID
}