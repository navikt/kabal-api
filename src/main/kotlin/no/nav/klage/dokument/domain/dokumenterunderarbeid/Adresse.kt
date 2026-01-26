package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Embeddable

@Embeddable
class Adresse(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String?,
    val poststed: String?,
    val landkode: String,
)