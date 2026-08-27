package no.nav.klage.oppgave.util

/**
 * Calculate median value
 */
fun getMedian(potentiallyUnsortedIntValues: List<Int>): Double {
    val sortedIntValues = potentiallyUnsortedIntValues.sorted()
    if (sortedIntValues.isEmpty()) {
        return 0.0
    }
    return if (sortedIntValues.size % 2 != 0) {
        sortedIntValues[sortedIntValues.size / 2].toDouble()
    } else {
        val val1 = sortedIntValues[(sortedIntValues.size / 2) - 1]
        val val2 = sortedIntValues[sortedIntValues.size / 2]

        if (val1 == val2) {
            val1.toDouble()
        } else {
            val diff = val2 - val1
            val1 + diff / 2.0
        }
    }
}
