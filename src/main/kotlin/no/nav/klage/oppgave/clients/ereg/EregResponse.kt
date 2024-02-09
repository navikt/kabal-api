package no.nav.klage.oppgave.clients.ereg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class Organisasjon(
    val navn: Navn,
    val organisasjonsnummer: String,
    val organisasjonDetaljer: OrganisasjonDetaljer,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Navn(
        val sammensattnavn: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OrganisasjonDetaljer(
        val opphoersdato: LocalDate?
    )

    //is "ifAfter" necessary?
    fun isActive() = organisasjonDetaljer.opphoersdato == null || organisasjonDetaljer.opphoersdato.isAfter(LocalDate.now())
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NoekkelInfoOmOrganisasjon(
    val navn: Navn,
    val organisasjonsnummer: String,
    val enhetstype: String,
    val opphoersdato: LocalDate?,
    val adresse: Adresse,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Navn(
        val sammensattnavn: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Adresse(
        val adresselinje1: String,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val landkode: String,
        val postnummer: String,
        val poststed: String?,
    )

    //is "ifAfter" necessary?
    fun isActive() = opphoersdato == null || opphoersdato.isAfter(LocalDate.now())
}
