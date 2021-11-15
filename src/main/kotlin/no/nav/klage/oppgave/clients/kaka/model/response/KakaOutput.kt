package no.nav.klage.oppgave.clients.kaka.model.response

import java.util.*

data class KakaOutput(
    val id: UUID
)

data class ValidationErrors(
    val validationErrors: List<InvalidProperty>
) {
    data class InvalidProperty(val field: String, val reason: String)
}