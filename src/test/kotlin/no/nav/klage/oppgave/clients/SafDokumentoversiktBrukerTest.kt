package no.nav.klage.oppgave.clients

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.klage.oppgave.clients.saf.graphql.DokumentoversiktBruker
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class SafDokumentoversiktBrukerTest {

    @MockK
    lateinit var tokenUtilMock: TokenUtil

    @BeforeEach
    fun before() {
        every { tokenUtilMock.getAppAccessTokenWithSafScope() } returns "abc"
        every { tokenUtilMock.getSaksbehandlerAccessTokenWithSafScope() } returns "abc"
    }

    @Test
    fun `saf response kan mappes selv om ikke alle felt er med i kotlin`() {
        val dokumentoversiktBrukerResponse = getDokumentoversiktBruker(dokumentoversiktResponse())
        assertThat(dokumentoversiktBrukerResponse.journalposter).hasSize(1)
        assertThat(dokumentoversiktBrukerResponse.journalposter.first().journalpostId).isEqualTo("492330029")
    }

    @Test
    fun `tom response fra saf er ogsaa gyldig`() {
        val dokumentoversiktBrukerResponse = getDokumentoversiktBruker(dokumentoversiktEmptyResponse())
        assertThat(dokumentoversiktBrukerResponse.journalposter).hasSize(0)
    }

    @Test
    fun `error response fra saf gir RuntimeException`() {
        assertThrows<RuntimeException> { getDokumentoversiktBruker(dokumentoversiktErrorResponse()) }
    }

    private fun getDokumentoversiktBruker(jsonResponse: String): DokumentoversiktBruker {
        val safClient = SafGraphQlClient(
            createShortCircuitWebClient(jsonResponse),
            tokenUtilMock,
        )

        return safClient.getDokumentoversiktBrukerAsSaksbehandler("fnr", emptyList(), 1, null)
    }

    @Language("json")
    fun dokumentoversiktResponse() = """
        {
          "data": {
            "dokumentoversiktBruker": {
              "journalposter": [
                {
                  "journalpostId": "492330029",
                  "tittel": "MASKERT_FELT",
                  "journalposttype": "I",
                  "journalstatus": "JOURNALFOERT",
                  "tema": "SYK",
                  "temanavn": "Sykepenger",
                  "behandlingstema": null,
                  "behandlingstemanavn": null,
                  "sak": {
                    "datoOpprettet": "2020-09-11T07:40:25",
                    "fagsakId": null,
                    "fagsaksystem": "FS22"
                  },
                  "bruker": {
                    "id": "1000034467123",
                    "type": "AKTOERID"
                  },
                  "avsenderMottaker": {
                    "id": "11126532778",
                    "type": "FNR",
                    "navn": "MASKERT_FELT",
                    "land": "NO",
                    "erLikBruker": true
                  },
                  "journalfoerendeEnhet": "9999",
                  "journalfortAvNavn": "Syfogsak",
                  "opprettetAvNavn": "Syfogsak",
                  "kanal": "NAV_NO",
                  "kanalnavn": "Ditt NAV",
                  "skjerming": null,
                  "datoOpprettet": "2020-12-14T12:22:03",
                  "relevanteDatoer": [
                    {
                      "dato": "2020-12-14T12:22:03",
                      "datotype": "DATO_DOKUMENT"
                    },
                    {
                      "dato": "2020-12-14T12:22:03",
                      "datotype": "DATO_JOURNALFOERT"
                    },
                    {
                      "dato": "2020-12-14T12:22:03",
                      "datotype": "DATO_REGISTRERT"
                    }
                  ],
                  "antallRetur": null,
                  "eksternReferanseId": null,
                  "tilleggsopplysninger": [],
                  "dokumenter": [
                    {
                      "dokumentInfoId": "514037527",
                      "tittel": "MASKERT_FELT",
                      "brevkode": "NAV 08-07.04 D",
                      "dokumentstatus": null,
                      "datoFerdigstilt": "2020-12-14T12:22:03",
                      "originalJournalpostId": "492330029",
                      "skjerming": null,
                      "dokumentvarianter": [
                        {
                          "variantformat": "ARKIV",
                          "filnavn": "Søknad om sykepenger 16.11.2020 - 10.12.2020",
                          "saksbehandlerHarTilgang": false,
                          "skjerming": null
                        }
                      ]
                    }
                  ]
                }
              ],
              "sideInfo": {
                "sluttpeker": "NDkyMzMwMDI5",
                "finnesNesteSide": true
              }
            }
          }
        }
    """

    @Language("json")
    fun dokumentoversiktEmptyResponse() = """
    {
      "data": {
        "dokumentoversiktBruker": {
          "journalposter": [],
          "sideInfo": {
            "sluttpeker": null,
            "finnesNesteSide": false
          }
        }
      }
    }
    """

    @Language("json")
    fun dokumentoversiktErrorResponse() = """
    {
      "errors": [
        {
          "message": "Field 'id' of variable 'brukerId' has coerced Null value for NonNull type 'String!'",
          "locations": [
            {
              "line": 1,
              "column": 7
            }
          ],
          "extensions": {
            "classification": "ValidationError"
          }
        }
      ]
    }
    """

}