package no.nav.klage.oppgave.api.view

enum class BrevMottagerRolle { KLAGER, SAKEN_GJELDER, PROSESSFULLMEKTIG }

data class BrevMottakerView(
    val partId: String,
    val partIdType: String,
    val navn: String?,
    val rolle: BrevMottagerRolle,
)
