package no.nav.klage.oppgave.util


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SortUtilTest {

    @Test
    fun `a comes before b`() {
        assertThat(
            listOf("b", "a").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "a",
            "b",
        )
    }

    @Test
    fun `a is in same order`() {
        assertThat(
            listOf("a", "a").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "a",
            "a",
        )
    }

    @Test
    fun `a1 is before a10`() {
        assertThat(
            listOf("a10", "a1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "a1",
            "a10",
        )
    }

    @Test
    fun `a_1 is before a_10`() {
        assertThat(
            listOf("a_10", "a_1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "a_1",
            "a_10",
        )
    }

    @Test
    fun `vedlegg1 is before vedlegg10`() {
        assertThat(
            listOf("vedlegg10", "vedlegg1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "vedlegg1",
            "vedlegg10",
        )
    }

    @Test
    fun `vedlegg 1 is before vedlegg 10`() {
        assertThat(
            listOf("vedlegg 10", "vedlegg 1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "vedlegg 1",
            "vedlegg 10",
        )
    }

    @Test
    fun `abc10abc is before abc100abc`() {
        assertThat(
            listOf("abc100abc", "abc10abc").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "abc10abc",
            "abc100abc",
        )
    }

    @Test
    fun `1abc is before 10abc`() {
        assertThat(
            listOf("10abc", "1abc").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "1abc",
            "10abc",
        )
    }

    @Test
    fun `a is before ab`() {
        assertThat(
            listOf("ab", "a").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "a",
            "ab",
        )
    }

    @Test
    fun `abc123 is before abcdef`() {
        assertThat(
            listOf("abcdef", "abc123").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "abc123",
            "abcdef",
        )
    }

    @Test
    fun `vedlegg 2 is before vedlegg 10`() {
        assertThat(
            listOf("vedlegg 10", "vedlegg 2").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "vedlegg 2",
            "vedlegg 10",
        )
    }

    @Test
    fun `vedlegg 3 del 2 is before vedlegg 3 del 10`() {
        assertThat(
            listOf("vedlegg 3 del 10", "vedlegg 3 del 2").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "vedlegg 3 del 2",
            "vedlegg 3 del 10",
        )
    }

    @Test
    fun `vedlegg 2 avsnitt 1 is before vedlegg 10 avsnitt 1`() {
        assertThat(
            listOf("vedlegg 10 avsnitt 1", "vedlegg 2 avsnitt 1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "vedlegg 2 avsnitt 1",
            "vedlegg 10 avsnitt 1",
        )
    }

    @Test
    fun `01-05-2014-vedlegg 2 is before 01-05-2014-vedlegg 2`() {
        assertThat(
            listOf("01-05-2014-vedlegg 2", "01-05-2014-vedlegg 1").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "01-05-2014-vedlegg 1",
            "01-05-2014-vedlegg 2",
        )
    }

    @Test
    fun `01-05-2014-vedlegg 1 avsnitt 18 is before 01-05-2014-vedlegg 2 avsnitt 16`() {
        assertThat(
            listOf("01-05-2014-vedlegg 2 avsnitt 16", "01-05-2014-vedlegg 1 avsnitt 18").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "01-05-2014-vedlegg 1 avsnitt 18",
            "01-05-2014-vedlegg 2 avsnitt 16",
        )
    }

    @Test
    fun `only first number matters`() {
        assertThat(
            listOf("Vedlegg 2 skutt", "Vedlegg 10 arst", "Vedlegg 3 skatt", "Vedlegg 0 regnskap").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "Vedlegg 0 regnskap",
            "Vedlegg 2 skutt",
            "Vedlegg 3 skatt",
            "Vedlegg 10 arst",
        )
    }

    @Test
    fun `only first number matters 2`() {
        assertThat(
            listOf("Vedlegg2skutt", "Vedlegg10arst", "Vedlegg3skatt", "Vedlegg0regnskap").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "Vedlegg0regnskap",
            "Vedlegg2skutt",
            "Vedlegg3skatt",
            "Vedlegg10arst",
        )
    }

    @Test
    fun `handle long number input string`() {
        assertThat(
            listOf("17170610291302148659684992526242", "17170610291302148659684992526244").sortedWith { o1, o2 -> compareStringsIncludingNumbers(o1, o2) }
        ).containsExactly(
            "17170610291302148659684992526242",
            "17170610291302148659684992526244",
        )
    }
}