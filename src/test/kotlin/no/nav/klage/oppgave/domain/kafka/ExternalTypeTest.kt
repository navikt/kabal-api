package no.nav.klage.oppgave.domain.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.kodeverk.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verdiene i [ExternalType] er en kontrakt mot fagsystemene. Disse testene feiler hvis noen endrer
 * dem, eller hvis en ny [Type] i kodeverket ikke blir mappet.
 */
class ExternalTypeTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `wire-verdiene er uendret`() {
        assertThat(ExternalType.entries.map { it.name })
            .containsExactlyInAnyOrder(
                "KLAGE",
                "ANKE",
                "ANKE_I_TRYGDERETTEN",
                "BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET",
                "OMGJOERINGSKRAV",
                "BEGJAERING_OM_GJENOPPTAK",
                "BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN",
            )
    }

    @Test
    fun `serialiseres til uendret json`() {
        assertThat(objectMapper.writeValueAsString(ExternalType.ANKE)).isEqualTo("\"ANKE\"")
        assertThat(objectMapper.writeValueAsString(ExternalType.ANKE_I_TRYGDERETTEN)).isEqualTo("\"ANKE_I_TRYGDERETTEN\"")
    }

    @Test
    fun `alle typer i kodeverket mappes til en ekstern type`() {
        Type.entries.forEach { type ->
            assertThat(type.toExternalType()).isNotNull()
        }
    }

    @Test
    fun `anke dekker baade foer og etter 2027`() {
        assertThat(ExternalType.ANKE.toTypes())
            .containsExactlyInAnyOrder(Type.ANKE_FOER_2027, Type.ANKE_ETTER_2027)
        assertThat(ExternalType.ANKE_I_TRYGDERETTEN.toTypes())
            .containsExactlyInAnyOrder(Type.ANKE_I_TRYGDERETTEN_FOER_2027, Type.ANKE_I_TRYGDERETTEN_ETTER_2027)
    }

    @Test
    fun `toTypes og toExternalType er konsistente`() {
        ExternalType.entries.forEach { externalType ->
            assertThat(externalType.toTypes())
                .allMatch { it.toExternalType() == externalType }
        }
    }
}
