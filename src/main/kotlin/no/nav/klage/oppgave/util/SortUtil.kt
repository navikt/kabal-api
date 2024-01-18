package no.nav.klage.oppgave.util

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid

class DokumentUnderArbeidTitleComparator : Comparator<DokumentUnderArbeid> {
    override fun compare(o1: DokumentUnderArbeid, o2: DokumentUnderArbeid): Int {
        return compareStringsIncludingNumbers(o1.name, o2.name)
    }
}

fun compareStringsIncludingNumbers(a: String, b: String): Int {
    val regex = "\\d+|\\D+".toRegex()

    val splitA = regex.findAll(a).map { it.value }.toList()
    val splitB = regex.findAll(b).map { it.value }.toList()

    for ((i, j) in splitA zip splitB) {
        val diff = if (i.first().isDigit() && j.first().isDigit()) {
            i.toInt().compareTo(j.toInt())
        } else {
            i.compareTo(j)
        }
        if (diff != 0) {
            return diff
        }
    }

    return 0
}
