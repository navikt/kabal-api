package no.nav.klage.oppgave.domain.person

import java.time.LocalDate

data class Person(
    val foedselsnr: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val sammensattNavn: String,
    val kjoenn: String?,
    val doed: LocalDate?,
    val strengtFortrolig: Boolean,
    val strengtFortroligUtland: Boolean,
    val fortrolig: Boolean,
    val egenAnsatt: Boolean,
    val vergemaalEllerFremtidsfullmakt: Boolean,
    val sikkerhetstiltak: Sikkerhetstiltak?,
    val protectedFamilyMembers: List<ProtectedFamilyMember>,
) {
    data class ProtectedFamilyMember(
        val foedselsnr: String,
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val sammensattNavn: String,
        val kjoenn: String?,
        val doed: LocalDate?,
        val strengtFortrolig: Boolean,
        val strengtFortroligUtland: Boolean,
        val fortrolig: Boolean,
        val egenAnsatt: Boolean,
    )

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
}