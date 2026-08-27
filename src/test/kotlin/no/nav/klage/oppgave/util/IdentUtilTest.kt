package no.nav.klage.oppgave.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdentUtilTest {
    @Test
    fun `ugyldig fødselsnummer gir feil`() {
        assertThat(isValidFnrOrDnr("12345678910")).isFalse
    }

    @Test
    fun `gyldig d-nummer gir rett svar`() {
        assertThat(isValidFnrOrDnr("02446701749")).isTrue
    }
}
