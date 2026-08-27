package no.nav.klage.oppgave.api.view

import no.nav.klage.oppgave.exceptions.ValidationSection

/** Only used when validation passed */
data class ValidationPassedResponse(
    val title: String = "Validation success",
    val status: Int = 200,
    val sections: List<ValidationSection> = emptyList(),
)
