package no.nav.klage.oppgave.api.view

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.kodeverk.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verdiene i [OversendtType] er en kontrakt mot fagsystemene som bruker oversendelses-API-ene.
 */
class OversendtTypeTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `wire-verdiene er uendret`() {
        assertThat(OversendtType.entries.map { it.name }).containsExactlyInAnyOrder("KLAGE", "ANKE")
    }

    @Test
    fun `serialiseres til uendret json`() {
        assertThat(objectMapper.writeValueAsString(OversendtType.ANKE)).isEqualTo("\"ANKE\"")
    }

    @Test
    fun `mapper til riktig type i kodeverket`() {
        assertThat(OversendtType.KLAGE.toType()).isEqualTo(Type.KLAGE)
        assertThat(OversendtType.ANKE.toType()).isEqualTo(Type.ANKE_FOER_2027)
    }
}
