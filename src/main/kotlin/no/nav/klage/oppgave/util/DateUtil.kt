package no.nav.klage.oppgave.util

import no.nav.klage.kodeverk.TimeUnitType
import java.time.LocalDate

fun findDateBasedOnTimeUnitTypeAndUnits(
    timeUnitType: TimeUnitType,
    units: Int,
    fromDate: LocalDate
): LocalDate {
    return when (timeUnitType) {
        TimeUnitType.WEEKS -> fromDate.plusWeeks(units.toLong())
        TimeUnitType.MONTHS -> fromDate.plusMonths(units.toLong())
    }
}
