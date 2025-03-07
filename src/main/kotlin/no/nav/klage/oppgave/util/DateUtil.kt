package no.nav.klage.oppgave.util

import no.nav.klage.kodeverk.TimeUnitType
import java.time.LocalDate

fun findDateBasedOnTimeUnitTypeAndUnits(
    timeUnitType: TimeUnitType,
    units: Long,
    fromDate: LocalDate
): LocalDate {
    return when (timeUnitType) {
        TimeUnitType.WEEKS -> fromDate.plusWeeks(units)
        TimeUnitType.MONTHS -> fromDate.plusMonths(units)
    }
}
