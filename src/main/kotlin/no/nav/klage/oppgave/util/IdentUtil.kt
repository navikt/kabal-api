package no.nav.klage.oppgave.util

fun isValidFnrOrDnr(fnr: String): Boolean {
    if (fnr.length != 11) {
        return false
    }
    val d1 = fnr.substring(0, 1).toInt()
    val d2 = fnr.substring(1, 2).toInt()
    val m1 = fnr.substring(2, 3).toInt()
    val m2 = fnr.substring(3, 4).toInt()
    val y1 = fnr.substring(4, 5).toInt()
    val y2 = fnr.substring(5, 6).toInt()
    val i1 = fnr.substring(6, 7).toInt()
    val i2 = fnr.substring(7, 8).toInt()
    val i3 = fnr.substring(8, 9).toInt()

    var k1 = 11 - ((3 * d1 + 7 * d2 + 6 * m1 + 1 * m2 + 8 * y1 + 9 * y2 + 4 * i1 + 5 * i2 + 2 * i3) % 11)
    var k2 = 11 - ((5 * d1 + 4 * d2 + 3 * m1 + 2 * m2 + 7 * y1 + 6 * y2 + 5 * i1 + 4 * i2 + 3 * i3 + 2 * k1) % 11)

    if (k1 == 11) k1 = 0
    if (k2 == 11) k2 = 0

    return k1 < 10 && k2 < 10 && k1 < 10 && k2 < 10 && k1 == fnr.substring(9, 10).toInt() && k2 == fnr.substring(10, 11)
        .toInt()
}

private val ORGNR_VEKTER = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)

fun isValidOrgnr(orgnr: String): Boolean {
    return if (orgnr.isEmpty()) {
        true
    } else {
        var sum = 0
        for (i in 0..7) {
            val verdi = orgnr.substring(i, i + 1).toInt()
            sum += ORGNR_VEKTER[i] * verdi
        }
        val rest = sum % 11
        val kontrollSiffer = if (rest == 0) 0 else 11 - rest
        kontrollSiffer == orgnr.substring(8, 9).toInt()
    }
}