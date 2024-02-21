package no.nav.klage.oppgave.domain.kodeverk

data class LandInfo(
    val land: String,
    val landkode: String,
)

data class PostInfo(
    val postnummer: String,
    val poststed: String,
)