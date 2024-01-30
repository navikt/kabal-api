package no.nav.klage.oppgave.api.view

data class VedtakUtfallInput(
    val utfallId: String?,
)

data class VedtakExtraUtfallSetInput(
    val extraUtfallIdSet: Set<String>,
)

data class VedtakHjemlerInput(
    val hjemmelIdSet: Set<String>,
)