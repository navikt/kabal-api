package no.nav.klage.oppgave.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class KakaVersionUtil(
    @Value($$"#{T(java.time.LocalDate).parse('${KAKA_VERSION_2_DATE}')}")
    private val kakaVersion2Date: LocalDate,
    @Value($$"#{T(java.time.LocalDate).parse('${KAKA_VERSION_3_DATE}')}")
    private val kakaVersion3Date: LocalDate,
) {
    fun getKakaVersion(): Int {
        val now = LocalDate.now()
        return when {
            now >= kakaVersion3Date -> 3
            now >= kakaVersion2Date -> 2
            else -> 1
        }
    }
}