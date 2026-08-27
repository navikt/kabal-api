package no.nav.klage.oppgave.clients

import no.nav.klage.oppgave.clients.norg2.Norg2Client
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Norg2ClientTest {
    @Test
    fun `json parsed correctly`() {
        val client = Norg2Client(createShortCircuitWebClient(okResponse))
        val enhet = client.fetchEnhet("1234")

        assertEquals("NAV-kontor", enhet.navn)
    }

    @Language("json")
    private val okResponse =
        """
        {
          "aktiveringsdato": "2018-01-01",
          "antallRessurser": 10,
          "enhetId": 10,
          "enhetNr": "1234",
          "kanalstrategi": "altiboks",
          "navn": "NAV-kontor",
          "nedleggelsesdato": "2018-01-01",
          "oppgavebehandler": true,
          "orgNivaa": "1",
          "orgNrTilKommunaltNavKontor": "123456789",
          "organisasjonsnummer": "987654321",
          "sosialeTjenester": "SOS",
          "status": "AKTIV",
          "type": "KONTOR",
          "underAvviklingDato": "2018-01-01",
          "underEtableringDato": "2018-01-01",
          "versjon": 1
        }
        """.trimIndent()
}
