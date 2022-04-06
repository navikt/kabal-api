package no.nav.klage.oppgave.api.view

enum class BrevmottakerRolle { KLAGER, SAKEN_GJELDER, PROSESSFULLMEKTIG }

data class BrevmottakerView(
    val partId: String,
    val partIdType: String,
    val navn: String?,
    val rolle: BrevmottakerRolle,
)
