package no.nav.klage.oppgave.util

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid

class DokumentUnderArbeidTitleComparator : Comparator<DokumentUnderArbeid> {
    override fun compare(o1: DokumentUnderArbeid, o2: DokumentUnderArbeid): Int {
        return compareStringsIncludingNumbers(o1.name, o2.name)
    }
}

fun compareStringsIncludingNumbers(a: String, b: String): Int {
    val regex = "\\d+".toRegex()

    //remove all numbers
    val aWithoutNumbers = a.replace(regex, "")
    val bWithoutNumbers = b.replace(regex, "")

    //if the if-check is true, then the strings are equal except the numbers
    return if (aWithoutNumbers == bWithoutNumbers &&
        a.length > aWithoutNumbers.length && //these are just making sure that the string did contain a number
        b.length > bWithoutNumbers.length
    ) {
        //get first number for a and b
        val aFirstNumber = regex.find(a)!!.value
        val bFirstNumber = regex.find(b)!!.value

        val indexOfANumber = a.indexOf(aFirstNumber)
        val indexOfBNumber = b.indexOf(bFirstNumber)

        //do the numbers start at the same place in the string?
        if (indexOfANumber == indexOfBNumber) {
            aFirstNumber.toInt().compareTo(bFirstNumber.toInt())
        } else {
            //if the numbers are on different locations in the strings, just run a normal compareTo()
            a.compareTo(b)
        }
    } else {
        a.compareTo(b)
    }
}
