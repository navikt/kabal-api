package no.nav.klage.oppgave.clients.saf.graphql

data class HentDokumentoversiktBrukerGraphqlQuery(
    val query: String,
    val variables: DokumentoversiktBrukerVariables,
)

data class DokumentoversiktBrukerVariables(
    val brukerId: BrukerId,
    val tema: List<Tema>?,
    val foerste: Int,
    val etter: String?,
)

data class BrukerId(
    val id: String,
    val type: BrukerIdType = BrukerIdType.FNR,
)

enum class BrukerIdType { FNR }

fun hentDokumentoversiktBrukerQuery(
    fnr: String,
    tema: List<Tema>?, // Hvis en tom liste er angitt som argument hentes journalposter på alle tema.
    pageSize: Int,
    previousPageRef: String?,
): HentDokumentoversiktBrukerGraphqlQuery {
    val journalpostProperties =
        HentJournalpostGraphqlQuery::class.java
            .getResource("/saf/journalpostProperties.txt")
            .readText()
    val query =
        HentDokumentoversiktBrukerGraphqlQuery::class.java
            .getResource("/saf/hentDokumentoversiktBruker.graphql")
            .readText()
            .replace(oldValue = "<replace>", newValue = journalpostProperties)
            .replace(oldValue = "[\n\r]", newValue = "")
    return HentDokumentoversiktBrukerGraphqlQuery(
        query = query,
        variables =
            DokumentoversiktBrukerVariables(
                brukerId = BrukerId(fnr),
                tema = if (tema.isNullOrEmpty()) null else tema,
                foerste = pageSize,
                etter = previousPageRef,
            ),
    )
}
