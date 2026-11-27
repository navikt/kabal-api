package no.nav.klage.oppgave.util

import java.time.LocalDate

object TrygderettenSaksnummer {
    private val pattern = Regex("^\\d{5,}$")
    private const val EARLIEST_YEAR = 1970

    fun isValid(
        value: String,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        if (!pattern.matches(value) || value.toIntOrNull() == null) {
            return false
        }

        val year = value.take(4).toInt()
        return year in EARLIEST_YEAR..(today.year + 1) && value.drop(4).toInt() > 0
    }
}
