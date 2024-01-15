package no.nav.klage.oppgave.util


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class SortUtilTest {
    @Test
    fun `a comes before b`() {
        assertThat(
            listOf("b", "a").sortedWith(TitleComparator())
        ).containsExactly(
            "a",
            "b",
        )
    }

    @Test
    fun `a is in same order`() {
        assertThat(
            listOf("a", "a").sortedWith(TitleComparator())
        ).containsExactly(
            "a",
            "a",
        )
    }

    @Test
    fun `a1 is before a10`() {
        assertThat(
            listOf("a10", "a1").sortedWith(TitleComparator())
        ).containsExactly(
            "a1",
            "a10",
        )
    }

    @Test
    fun `a_1 is before a_10`() {
        assertThat(
            listOf("a_10", "a_1").sortedWith(TitleComparator())
        ).containsExactly(
            "a_1",
            "a_10",
        )
    }

    @Test
    fun `vedlegg1 is before vedlegg10`() {
        assertThat(
            listOf("vedlegg10", "vedlegg1").sortedWith(TitleComparator())
        ).containsExactly(
            "vedlegg1",
            "vedlegg10",
        )
    }

    @Test
    fun `vedlegg 1 is before vedlegg 10`() {
        assertThat(
            listOf("vedlegg 10", "vedlegg 1").sortedWith(TitleComparator())
        ).containsExactly(
            "vedlegg 1",
            "vedlegg 10",
        )
    }

    @Test
    fun `abc10abc is before abc100abc`() {
        assertThat(
            listOf("abc100abc", "abc10abc").sortedWith(TitleComparator())
        ).containsExactly(
            "abc10abc",
            "abc100abc",
        )
    }

    @Test
    fun `1abc is before 10abc`() {
        assertThat(
            listOf("10abc", "1abc").sortedWith(TitleComparator())
        ).containsExactly(
            "1abc",
            "10abc",
        )
    }

    @Test
    fun `a is before ab`() {
        assertThat(
            listOf("ab", "a").sortedWith(TitleComparator())
        ).containsExactly(
            "a",
            "ab",
        )
    }

    @Test
    fun `abc123 is before abcdef`() {
        assertThat(
            listOf("abcdef", "abc123").sortedWith(TitleComparator())
        ).containsExactly(
            "abc123",
            "abcdef",
        )
    }
}