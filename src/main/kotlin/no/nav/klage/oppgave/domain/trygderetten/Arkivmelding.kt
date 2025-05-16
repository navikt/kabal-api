package no.nav.klage.oppgave.domain.trygderetten

import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory


fun Arkivmelding.toXml(): String {
    return xml("arkivmelding") {
        xmlns = "http://www.arkivverket.no/standarder/noark5/arkivmelding"
        namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
        attribute("xsi:schemaLocation", "http://www.arkivverket.no/standarder/noark5/arkivmelding arkivmelding.xsd")
        "system" { -system }
        "meldingId" { -meldingId }
        "tidspunkt" { -tidspunkt.truncatedToSeconds() }
        "antallFiler" { -antallFiler.toString() }
        "mappe" {
            attribute("xsi:type", "saksmappe")
            "tittel" { -mappe.tittel }
            "opprettetDato" { -mappe.opprettetDato.truncatedToSeconds() }
            "virksomhetsspesifikkeMetadata" { -mappe.virksomhetsspesifikkeMetadata }
            "part" {
                "partNavn" { -mappe.part.partNavn }
                "partRolle" { -mappe.part.partRolle }
                mappe.part.organisasjonsnummer?.organisasjonsnummer?.let {
                    "organisasjonsnummer" {
                        "organisasjonsnummer" { -mappe.part.organisasjonsnummer.organisasjonsnummer }
                    }
                }
                mappe.part.foedselsnummer?.foedselsnummer?.let {
                    "foedselsnummer" {
                        "foedselsnummer" { -mappe.part.foedselsnummer.foedselsnummer }
                    }
                }
                mappe.part.kontaktperson?.let {
                    "kontaktperson" { -mappe.part.kontaktperson }
                }
            }
            "registrering" {
                attribute("xsi:type", "journalpost")
                "opprettetDato" { -mappe.registrering.opprettetDato.truncatedToSeconds() }
                "opprettetAv" { -mappe.registrering.opprettetAv }
                "dokumentbeskrivelse" {
                    "dokumenttype" { -mappe.registrering.dokumentbeskrivelse.dokumenttype }
                    "dokumentstatus" { -mappe.registrering.dokumentbeskrivelse.dokumentstatus }
                    "tittel" { -mappe.registrering.dokumentbeskrivelse.tittel }
                    "opprettetDato" { -mappe.registrering.dokumentbeskrivelse.opprettetDato.truncatedToSeconds() }
                    "opprettetAv" { -mappe.registrering.dokumentbeskrivelse.opprettetAv }
                    "tilknyttetRegistreringSom" { -mappe.registrering.dokumentbeskrivelse.tilknyttetRegistreringSom }
                    "dokumentnummer" { -mappe.registrering.dokumentbeskrivelse.dokumentnummer.toString() }
                    "tilknyttetDato" { -mappe.registrering.dokumentbeskrivelse.tilknyttetDato.truncatedToSeconds() }
                    "tilknyttetAv" { -mappe.registrering.dokumentbeskrivelse.tilknyttetAv }
                    "dokumentobjekt" {
                        "versjonsnummer" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.versjonsnummer.toString() }
                        "variantformat" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.variantformat }
                        "format" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.format }
                        "opprettetDato" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.opprettetDato.truncatedToSeconds() }
                        "opprettetAv" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.opprettetAv }
                        "referanseDokumentfil" { -mappe.registrering.dokumentbeskrivelse.dokumentobjekt.referanseDokumentfil }
                    }
                }
                "tittel" { -mappe.registrering.tittel }
                "korrespondansepart" {
                    "korrespondanseparttype" { -mappe.registrering.korrespondansepart.korrespondanseparttype }
                    "korrespondansepartNavn" { -mappe.registrering.korrespondansepart.korrespondansepartNavn }
                    "organisasjonsnummer" {
                        "organisasjonsnummer" { -mappe.registrering.korrespondansepart.organisasjonsnummer.organisasjonsnummer }
                    }
                }
                "journalposttype" { -mappe.registrering.journalposttype }
                "journalstatus" { -mappe.registrering.journalstatus }
                "journaldato" { -mappe.registrering.journaldato.toString() }
            }
            "saksdato" { -mappe.saksdato.toString() }
            "administrativEnhet" { -mappe.administrativEnhet }
            "saksansvarlig" { -mappe.saksansvarlig }
            "journalenhet" { -mappe.journalenhet }
            "saksstatus" { -mappe.saksstatus }
        }
    }.toString(PrintOptions(singleLineTextElements = true))
}

private fun LocalDateTime.truncatedToSeconds() = this.truncatedTo(ChronoUnit.SECONDS).toString()

class Arkivmelding(
    //Antageligvis Kabal, for å kjenne igjen våre forsendelser
    val system: String,
    //Skal vi sette denne? Virker som om den hentes fra en forsendelsesbestilling
    val meldingId: String,
    //now()
    val tidspunkt: LocalDateTime,
    //Henter fra det vi skal sende inn til kabal-document
    val antallFiler: Int,
    val mappe: Mappe
) {

    data class Mappe(
        val tittel: String,
        val opprettetDato: LocalDateTime,
        val virksomhetsspesifikkeMetadata: String,
        val part: Part,
        val registrering: Registrering,
        val saksdato: LocalDate,
        val administrativEnhet: String,
        val saksansvarlig: String,
        val journalenhet: String,
        val saksstatus: String

    ) {
        data class Part(
            val partNavn: String,
            val partRolle: String,
            val organisasjonsnummer: Organisasjonsnummer?,
            val foedselsnummer: Foedselsnummer?,
            val kontaktperson: String?
        ) {

            data class Foedselsnummer(
                val foedselsnummer: String
            )
        }

        data class Registrering(
            val opprettetDato: LocalDateTime,
            val opprettetAv: String,
            val dokumentbeskrivelse: Dokumentbeskrivelse,
            val tittel: String,
            val korrespondansepart: Korrespondansepart,
            val journalposttype: String,
            val journalstatus: String,
            val journaldato: LocalDate

        ) {
            data class Dokumentbeskrivelse(
                val dokumenttype: String,
                val dokumentstatus: String,
                val tittel: String,
                val opprettetDato: LocalDateTime,
                val opprettetAv: String,
                val tilknyttetRegistreringSom: String,
                val dokumentnummer: Int,
                val tilknyttetDato: LocalDateTime,
                val tilknyttetAv: String,
                val dokumentobjekt: Dokumentobjekt

            ) {
                data class Dokumentobjekt(
                    val versjonsnummer: Int,
                    val format: String,
                    val variantformat: String,
                    val opprettetDato: LocalDateTime,
                    val opprettetAv: String,
                    val referanseDokumentfil: String
                )
            }

            data class Korrespondansepart(
                val korrespondanseparttype: String,
                val korrespondansepartNavn: String,
                val organisasjonsnummer: Organisasjonsnummer

            )
        }
    }

    data class Organisasjonsnummer(
        val organisasjonsnummer: String
    )
}

fun validateXmlAgainstXsd(xml: String) {
    val factory: SchemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema")

    val schema = factory.newSchema(ClassPathResource("schema/arkivmelding.xsd").file)
    val validator = schema.newValidator()

    val source = StreamSource(xml.byteInputStream())

    println("Validation Starts now!")

    validator.validate(source)
}
