package no.nav.klage.oppgave.clients.norg2

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnhetResponse(
    val navn: String,
    val enhetNr: String,
) {
    fun asEnhet() = Enhet(enhetsnr = enhetNr, navn = navn)
}
