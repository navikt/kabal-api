package no.nav.klage.oppgave.domain.person

import java.time.LocalDate

data class Person(
    val foedselsnr: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val beskyttelsesbehov: Beskyttelsesbehov?,
    val kjoenn: String?,
    val vergemaalEllerFremtidsfullmakt: Boolean,
    val doed: LocalDate?,
    val sikkerhetstiltak: Sikkerhetstiltak?,
) {
    fun harBeskyttelsesbehovFortrolig() = beskyttelsesbehov == Beskyttelsesbehov.FORTROLIG

    fun harBeskyttelsesbehovStrengtFortrolig() =
        beskyttelsesbehov == Beskyttelsesbehov.STRENGT_FORTROLIG || beskyttelsesbehov == Beskyttelsesbehov.STRENGT_FORTROLIG_UTLAND

    fun settSammenNavn(): String {
        return if (mellomnavn != null) {
            "$fornavn $mellomnavn $etternavn"
        } else {
            "$fornavn $etternavn"
        }
    }
}

data class Sikkerhetstiltak(
    val tiltakstype: Tiltakstype,
    val beskrivelse: String,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
) {
    enum class Tiltakstype {
        FYUS,
        TFUS,
        FTUS,
        DIUS,
        TOAN,
    }
}

enum class Beskyttelsesbehov {
    STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, FORTROLIG
}
