package no.nav.klage.oppgave.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StatsUtilsTest {
    @Test
    fun `median works when list size is even`() {
        val list = listOf(11, 5, 10, 1)
        assertThat(getMedian(list)).isEqualTo(7.5)
    }

    @Test
    fun `median works when list size is even and middle values are the same`() {
        val list = listOf(10, 11, 10, 1)
        assertThat(getMedian(list)).isEqualTo(10.0)
    }

    @Test
    fun `median works when size is uneven`() {
        val list = listOf(10, 11, 1, 5, 12)
        assertThat(getMedian(list)).isEqualTo(10.0)
    }

    @Test
    fun `median works for empty lists`() {
        val list = emptyList<Int>()
        assertThat(getMedian(list)).isEqualTo(0.0)
    }

    @Test
    fun `median works for lists with only one element`() {
        val list = listOf(1)
        assertThat(getMedian(list)).isEqualTo(1.0)
    }
}
