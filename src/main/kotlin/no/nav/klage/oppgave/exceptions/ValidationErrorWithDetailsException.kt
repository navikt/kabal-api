package no.nav.klage.oppgave.exceptions

class ValidationErrorWithDetailsException(val title: String, val invalidProperties: List<InvalidProperty>) :
    RuntimeException()

data class InvalidProperty(val field: String, val reason: String)