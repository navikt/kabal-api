package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class DokumentUnderArbeidAdresse(
    @Column(name = "adresselinje_1")
    val adresselinje1: String,
    @Column(name = "adresselinje_2")
    val adresselinje2: String?,
    @Column(name = "adresselinje_3")
    val adresselinje3: String?,
    @Column(name = "postnummer")
    val postnummer: String,
    @Column(name = "poststed")
    val poststed: String,
    @Column(name = "landkode")
    val landkode: String,
)