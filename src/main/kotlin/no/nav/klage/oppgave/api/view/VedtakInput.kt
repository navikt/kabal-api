package no.nav.klage.oppgave.api.view

data class VedtakUtfallInput(
    val utfall: String?,
    val utfallId: String?,
)

data class VedtakExtraUtfallSetInput(
    val extraUtfallIdSet: Set<String>,
)

data class VedtakHjemlerInput(
    val hjemler: Set<String>?,
    val hjemmelIdSet: Set<String>?,
)