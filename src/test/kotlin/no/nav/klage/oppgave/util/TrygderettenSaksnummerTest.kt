package no.nav.klage.oppgave.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrygderettenSaksnummerTest {
    private val today = LocalDate.of(2026, 6, 15)

    @Test
    fun `accepts years on both boundaries and this year`() {
        assertThat(TrygderettenSaksnummer.isValid(value = "19701", today = today)).isTrue()
        assertThat(TrygderettenSaksnummer.isValid(value = "20261", today = today)).isTrue()
        assertThat(TrygderettenSaksnummer.isValid(value = "20271", today = today)).isTrue()
    }

    @Test
    fun `rejects years outside the allowed range`() {
        assertThat(TrygderettenSaksnummer.isValid(value = "19691", today = today)).isFalse()
        assertThat(TrygderettenSaksnummer.isValid(value = "20281", today = today)).isFalse()
    }

    @Test
    fun `rejects zero counter`() {
        assertThat(TrygderettenSaksnummer.isValid(value = "20260", today = today)).isFalse()
        assertThat(TrygderettenSaksnummer.isValid(value = "202600", today = today)).isFalse()
    }

    @Test
    fun `rejects blank short and non-digit values`() {
        listOf("", " ", "1234", "2026a", "2026-1", " 20261", "20261 ").forEach {
            assertThat(TrygderettenSaksnummer.isValid(value = it, today = today)).isFalse()
        }
    }

    @Test
    fun `rejects values that do not fit in an integer`() {
        assertThat(TrygderettenSaksnummer.isValid(value = "2147483648", today = today)).isFalse()
        assertThat(TrygderettenSaksnummer.isValid(value = "20262147483648", today = today)).isFalse()
    }

    @Test
    fun `accepts a positive counter`() {
        assertThat(TrygderettenSaksnummer.isValid(value = "2026123", today = today)).isTrue()
        assertThat(TrygderettenSaksnummer.isValid(value = "2026123456", today = today)).isTrue()
    }
}
