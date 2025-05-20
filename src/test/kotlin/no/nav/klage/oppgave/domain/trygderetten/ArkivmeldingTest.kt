package no.nav.klage.oppgave.domain.trygderetten

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class ArkivmeldingTest {

    @Test
    fun `create xml`() {
        val arkivmelding = Arkivmelding(
            system = "system",
            meldingId = "meldingsId",
            tidspunkt = LocalDateTime.now(),
            antallFiler = 1,
            mappe = Arkivmelding.Mappe(
                tittel = "tittel",
                opprettetDato = LocalDateTime.now(),
                virksomhetsspesifikkeMetadata = "virksomhetsspesifikkeMetadata",
                part = Arkivmelding.Mappe.Part(
                    partNavn = "partNavn",
                    partRolle = "partRolle",
                    organisasjonsnummer = null,
//                            Arkivmelding.Organisasjonsnummer(
//                        organisasjonsnummer = "organisasjonsnummer"
//                    ),
                    foedselsnummer = Arkivmelding.Mappe.Part.Foedselsnummer(
                        foedselsnummer = "foedselsnummer"
                    ),
                    kontaktperson = "kontaktperson"
                ),
                registrering = Arkivmelding.Mappe.Registrering(
                    opprettetDato = LocalDateTime.now(),
                    opprettetAv = "opprettetAv",
                    dokumentbeskrivelse = Arkivmelding.Mappe.Registrering.Dokumentbeskrivelse(
                        dokumenttype = "dokumenttype",
                        dokumentstatus = "dokumentstatus",
                        tittel = "tittel",
                        opprettetDato = LocalDateTime.now(),
                        opprettetAv = "oppretteAv",
                        tilknyttetRegistreringSom = "tilknyttetRegistreringSom",
                        dokumentnummer = 1,
                        tilknyttetDato = LocalDateTime.now(),
                        tilknyttetAv = "tilknyttetAv",
                        dokumentobjekt = Arkivmelding.Mappe.Registrering.Dokumentbeskrivelse.Dokumentobjekt(
                            versjonsnummer = 1,
                            format = "format",
                            variantformat = "variantformat",
                            opprettetDato = LocalDateTime.now(),
                            opprettetAv = "opprettetAv",
                            referanseDokumentfil = "referanseDokumentfil"
                        )
                    ),
                    tittel = "tittel",
                    korrespondansepart = Arkivmelding.Mappe.Registrering.Korrespondansepart(
                        korrespondanseparttype = "korrespondanseparttype",
                        korrespondansepartNavn = "korrespondansepartNavn",
                        organisasjonsnummer = Arkivmelding.Organisasjonsnummer(
                            organisasjonsnummer = "organisasjonsnummer"
                        )
                    ),
                    journalposttype = "journalposttype",
                    journalstatus = "journalstatus",
                    journaldato = LocalDate.now()
                ),
                saksdato = LocalDate.now(),
                administrativEnhet = "administrativEnhet",
                saksansvarlig = "saksansvarlig",
                journalenhet = "journalenhet",
                saksstatus = "saksstatus"
            )
        )
        val xml = arkivmelding.toXml()
//        println(xml)
        validateXmlAgainstXsd(xml)
    }

}