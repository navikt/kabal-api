package no.nav.klage.oppgave.domain.klage

data class Access(
    val access: Boolean,
    val reason: String? = null,
)