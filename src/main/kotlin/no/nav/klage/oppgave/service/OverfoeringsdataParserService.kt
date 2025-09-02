package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.behandling.KLAGEENHET_PREFIX
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class OverfoeringsdataParserService {

    private val newCommentRegex =
        Regex(
            """\-\-\-\s(\d\d\.\d\d\.\d\d\d\d)\s\d\d\:\d\d\s[^(]*\s\(([^,]*)\,\s(\d{4})\)\s\-\-\-""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
        )

    private val enhetOverfoeringRegex =
        Regex(""".*?fra\senhet\s(\d{4})\stil\s(42\d\d).*?(?=${'$'})""")

    fun parseBeskrivelse(beskrivelse: String): Overfoeringsdata? {
        val iterator = beskrivelse.lineSequence().iterator()
        var saksbehandlerWhoMadeTheChange: String? = null
        var enhetOfsaksbehandlerWhoMadeTheChange: String? = null
        var datoForOverfoering: LocalDate? = null
        var enhetOverfoertFra: String? = null
        var enhetOverfoertTil: String? = null

        while (iterator.hasNext() && (saksbehandlerWhoMadeTheChange == null || enhetOfsaksbehandlerWhoMadeTheChange == null || enhetOverfoertFra == null || enhetOverfoertTil == null || datoForOverfoering == null)) {
            val line = iterator.next()
            if (newCommentRegex.containsMatchIn(line)) {
                val values = newCommentRegex.find(line)?.groupValues!!
                datoForOverfoering = LocalDate.parse(values[1], DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                saksbehandlerWhoMadeTheChange = values[2]
                enhetOfsaksbehandlerWhoMadeTheChange = values[3]
            } else if (enhetOverfoeringRegex.containsMatchIn(line)) {
                val values = enhetOverfoeringRegex.find(line)?.groupValues!!
                enhetOverfoertFra = values[1]
                enhetOverfoertTil = values[2]
            }
        }
        return if (saksbehandlerWhoMadeTheChange == null || enhetOfsaksbehandlerWhoMadeTheChange == null || enhetOverfoertFra == null || enhetOverfoertTil == null || datoForOverfoering == null) {
            parseBeskrivelseAnnenMaate(beskrivelse)
        } else {
            Overfoeringsdata(
                saksbehandlerWhoMadeTheChange,
                enhetOfsaksbehandlerWhoMadeTheChange,
                datoForOverfoering,
                enhetOverfoertFra,
                enhetOverfoertTil
            )
        }
    }

    private fun parseBeskrivelseAnnenMaate(beskrivelse: String): Overfoeringsdata? =
        beskrivelse.lineSequence().filter { newCommentRegex.containsMatchIn(it) }.map {
            val values = newCommentRegex.find(it)?.groupValues!!
            val datoForOverfoering = LocalDate.parse(values[1], DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val saksbehandlerWhoMadeTheChange = values[2]
            val enhetOfsaksbehandlerWhoMadeTheChange = values[3]
            Triple(
                saksbehandlerWhoMadeTheChange,
                enhetOfsaksbehandlerWhoMadeTheChange,
                datoForOverfoering
            )
        }.zipWithNext().firstOrNull {
            it.first.second.startsWith(KLAGEENHET_PREFIX) && !it.second.second.startsWith(
                KLAGEENHET_PREFIX
            )
        }?.let {
            Overfoeringsdata(
                it.second.first,
                it.second.second,
                it.second.third,
                it.second.second,
                it.first.second
            )
        }
}

data class Overfoeringsdata(
    val saksbehandlerWhoMadeTheChange: String,
    val enhetOfsaksbehandlerWhoMadeTheChange: String,
    val datoForOverfoering: LocalDate,
    val enhetOverfoertFra: String,
    val enhetOverfoertTil: String
)
