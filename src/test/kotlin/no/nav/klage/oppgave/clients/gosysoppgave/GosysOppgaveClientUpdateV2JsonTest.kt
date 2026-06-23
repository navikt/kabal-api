package no.nav.klage.oppgave.clients.gosysoppgave

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream

class GosysOppgaveClientUpdateV2JsonTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("updateOppgaveInputVariants")
    fun `all updateOppgaveInput variants are serialized as expected`(
        @Suppress("UNUSED_PARAMETER")
        scenario: String,
        updateOppgaveInput: UpdateOppgaveRequest,
        expectedJson: String,
    ) {
        val requestBodies = mutableListOf<String>()
        val tokenUtil = mockk<TokenUtil>()
        every { tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope() } returns "dummy-token"

        val client = GosysOppgaveClient(
            gosysOppgaveWebClient = createCapturingWebClient(requestBodies),
            tokenUtil = tokenUtil,
            applicationName = "kabal-api",
        )

        client.updateGosysOppgave(
            gosysOppgaveId = 123L,
            updateOppgaveInput = updateOppgaveInput,
            systemContext = false,
        )

        assertEquals(1, requestBodies.size)
        assertJsonEquals(expectedJson, requestBodies.single())
    }

    private fun createCapturingWebClient(requestBodies: MutableList<String>): WebClient {
        val response = ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(minimalGosysResponseBody)
            .build()

        val exchangeFunction = ExchangeFunction { request ->
            val mockRequest = MockClientHttpRequest(request.method(), request.url())
            request.body().insert(mockRequest, bodyInserterContext).block()
            requestBodies += mockRequest.bodyAsString.block() ?: ""
            Mono.just(response)
        }

        return WebClient.builder().exchangeFunction(exchangeFunction).build()
    }

    private fun assertJsonEquals(expectedJson: String, actualJson: String) {
        assertEquals(objectMapper.readTree(expectedJson), objectMapper.readTree(actualJson))
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()

        private const val minimalGosysResponseBody =
            """
            {
              "id": 1,
              "kategorisering": {
                "tema": {"kode": "KLA", "term": "Klage"},
                "oppgavetype": {"kode": "BEH", "term": "Behandle sak"},
                "behandlingstema": null,
                "behandlingstype": null
              },
              "fordeling": {
                "enhet": {"nr": "9999"},
                "mappe": null,
                "medarbeider": null
              },
              "prioritet": "NORMAL",
              "beskrivelse": "ok",
              "nokkelord": [],
              "aktivDato": "2026-06-22",
              "fristDato": "2026-06-30",
              "versjon": 1,
              "bruker": null,
              "status": "AAPEN",
              "opprettet": {
                "tidspunkt": "2026-06-22T12:00:00",
                "av": null
              },
              "endret": null,
              "lukket": null,
              "kommentarer": []
            }
            """

        private val bodyInserterContext = object : BodyInserter.Context {
            override fun messageWriters(): List<HttpMessageWriter<*>> =
                ExchangeStrategies.withDefaults().messageWriters()

            override fun serverRequest(): Optional<ServerHttpRequest> = Optional.empty()

            override fun hints(): Map<String, Any> = emptyMap()
        }

        @JvmStatic
        fun updateOppgaveInputVariants(): Stream<Arguments> {
            val enhet = EnhetDto(nr = "4299")
            val representerer = Representerer(enhet = EnhetDto(nr = "1111"))
            val medarbeider = MedarbeiderRequestDto(navident = "Z123456")
            val mappe = MappeRequestDto(id = 77)
            val tildeling = FordelingTildelingRequest(enhet = enhet, mappe = mappe, medarbeider = medarbeider)
            val fradeling = FordelingFradelingRequest(medarbeider = null)
            val kommentar = "Knytter saken til behandling"
            val fristDato = LocalDate.of(2026, 6, 30)

            return Stream.of(
                Arguments.of(
                    "Tildel uten representerer",
                    TildelGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMeta(versjon = 1),
                        fordeling = tildeling,
                    ),
                    """
                    {
                      "meta": {"versjon": 1},
                      "fordeling": {
                        "enhet": {"nr": "4299"},                        
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      }
                    }
                    """,
                ),
                Arguments.of(
                    "Tildel med representerer",
                    TildelGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithRepresenterer(versjon = 2, representerer = representerer),
                        fordeling = tildeling,
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 2,
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "fordeling": {
                        "enhet": {"nr": "4299"},
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      }
                    }
                    """,
                ),
                Arguments.of(
                    "Fradel uten representerer",
                    FradelGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMeta(versjon = 3),
                        fordeling = fradeling,
                    ),
                    """
                    {
                      "meta": {"versjon": 3},
                      "fordeling": {"medarbeider": null}
                    }
                    """,
                ),
                Arguments.of(
                    "Fradel med representerer",
                    FradelGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithRepresenterer(versjon = 4, representerer = representerer),
                        fordeling = fradeling,
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 4,
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "fordeling": {"medarbeider": null}
                    }
                    """,
                ),
                Arguments.of(
                    "Legg til kommentar uten representerer",
                    AddKommentarToGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMetaWithKommentar(versjon = 5, kommentar = kommentar),
                    ),
                    """
                    {
                      "meta": {"versjon": 5, "kommentar": "Knytter saken til behandling"}
                    }
                    """,
                ),
                Arguments.of(
                    "Legg til kommentar med representerer",
                    AddKommentarToGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithKommentarAndRepresenterer(
                            versjon = 6,
                            kommentar = kommentar,
                            representerer = representerer
                        ),
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 6,
                        "kommentar": "Knytter saken til behandling",
                        "representerer": {"enhet": {"nr": "1111"}}
                      }
                    }
                    """,
                ),
                Arguments.of(
                    "Oppdater frist uten representerer",
                    UpdateFristInGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMetaWithKommentar(versjon = 7, kommentar = kommentar),
                        fristDato = fristDato,
                    ),
                    """
                    {
                      "meta": {"versjon": 7, "kommentar": "Knytter saken til behandling"},
                      "fristDato": "2026-06-30"
                    }
                    """,
                ),
                Arguments.of(
                    "Oppdater frist med representerer",
                    UpdateFristInGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithKommentarAndRepresenterer(
                            versjon = 8,
                            kommentar = kommentar,
                            representerer = representerer
                        ),
                        fristDato = fristDato,
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 8,
                        "kommentar": "Knytter saken til behandling",
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "fristDato": "2026-06-30"
                    }
                    """,
                ),
                Arguments.of(
                    "Avslutt uten representerer",
                    AvsluttGosysOppgaveRequestWithoutRepresenterer(
                        meta = PatchMetaWithKommentar(versjon = 9, kommentar = kommentar),
                        status = GosysOppgaveRecordV2.StatusV2.FERDIGSTILT,
                    ),
                    """
                    {
                      "meta": {"versjon": 9, "kommentar": "Knytter saken til behandling"},
                      "status": "FERDIGSTILT"
                    }
                    """,
                ),
                Arguments.of(
                    "Avslutt med representerer",
                    AvsluttGosysOppgaveRequestWithRepresenterer(
                        meta = PatchMetaWithKommentarAndRepresenterer(
                            versjon = 10,
                            kommentar = kommentar,
                            representerer = representerer
                        ),
                        status = GosysOppgaveRecordV2.StatusV2.FEILREGISTRERT,
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 10,
                        "kommentar": "Knytter saken til behandling",
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "status": "FEILREGISTRERT"
                    }
                    """,
                ),
                Arguments.of(
                    "Full oppdatering ved ferdigstilt behandling uten representerer",
                    UpdateGosysOppgaveOnCompletedBehandlingRequestWithoutRepresenterer(
                        meta = PatchMetaWithKommentar(versjon = 11, kommentar = kommentar),
                        fristDato = fristDato,
                        fordeling = tildeling,
                    ),
                    """
                    {
                      "meta": {"versjon": 11, "kommentar": "Knytter saken til behandling"},
                      "fristDato": "2026-06-30",
                      "fordeling": {
                        "enhet": {"nr": "4299"},
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      }
                    }
                    """,
                ),
                Arguments.of(
                    "Full oppdatering ved ferdigstilt behandling uten representerer og med nokkelord",
                    UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithoutRepresenterer(
                        meta = PatchMetaWithKommentar(versjon = 12, kommentar = kommentar),
                        fristDato = fristDato,
                        fordeling = tildeling,
                        nokkelord = setOf("HAST"),
                    ),
                    """
                    {
                      "meta": {"versjon": 12, "kommentar": "Knytter saken til behandling"},
                      "fristDato": "2026-06-30",
                      "fordeling": {
                        "enhet": {"nr": "4299"},
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      },
                      "nokkelord": ["HAST"]
                    }
                    """,
                ),
                Arguments.of(
                    "Full oppdatering ved ferdigstilt behandling med representerer",
                    UpdateGosysOppgaveOnCompletedBehandlingRequestWithRepresenterer(
                        meta = PatchMetaWithKommentarAndRepresenterer(
                            versjon = 13,
                            kommentar = kommentar,
                            representerer = representerer
                        ),
                        fristDato = fristDato,
                        fordeling = tildeling,
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 13,
                        "kommentar": "Knytter saken til behandling",
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "fristDato": "2026-06-30",
                      "fordeling": {
                        "enhet": {"nr": "4299"},
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      }
                    }
                    """,
                ),
                Arguments.of(
                    "Full oppdatering ved ferdigstilt behandling med representerer og nokkelord",
                    UpdateGosysOppgaveOnCompletedBehandlingRequestWithNokkelordAndWithRepresenterer(
                        meta = PatchMetaWithKommentarAndRepresenterer(
                            versjon = 14,
                            kommentar = kommentar,
                            representerer = representerer
                        ),
                        fristDato = fristDato,
                        fordeling = tildeling,
                        nokkelord = setOf("HAST"),
                    ),
                    """
                    {
                      "meta": {
                        "versjon": 14,
                        "kommentar": "Knytter saken til behandling",
                        "representerer": {"enhet": {"nr": "1111"}}
                      },
                      "fristDato": "2026-06-30",
                      "fordeling": {
                        "enhet": {"nr": "4299"},
                        "mappe": {"id": 77},
                        "medarbeider": {"navident": "Z123456"}
                      },
                      "nokkelord": ["HAST"]
                    }
                    """,
                ),
            )
        }
    }
}


