package no.nav.klage.oppgave.clients.pdl.graphql

import no.nav.klage.oppgave.clients.pdl.Beskyttelsesbehov
import no.nav.klage.oppgave.clients.pdl.Person
import no.nav.klage.oppgave.clients.pdl.Sikkerhetstiltak
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Component

@Component
class HentPersonMapper {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun mapToPerson(fnr: String, pdlPerson: PdlPerson): Person {
        return Person(
            foedselsnr = fnr,
            fornavn = pdlPerson.navn.first().fornavn,
            mellomnavn = pdlPerson.navn.first().mellomnavn,
            etternavn = pdlPerson.navn.first().etternavn,
            beskyttelsesbehov = pdlPerson.adressebeskyttelse.firstOrNull()?.gradering?.mapToBeskyttelsesbehov(),
            kjoenn = pdlPerson.kjoenn.firstOrNull()?.kjoenn?.name,
            vergemaalEllerFremtidsfullmakt = pdlPerson.vergemaalEllerFremtidsfullmakt.isNotEmpty(),
            doed = pdlPerson.doedsfall.firstOrNull()?.doedsdato,
            sikkerhetstiltak = pdlPerson.sikkerhetstiltak.firstOrNull()?.mapToSikkerhetstiltak(),
        )
    }

    fun PdlPerson.Adressebeskyttelse.GraderingType.mapToBeskyttelsesbehov(): Beskyttelsesbehov? =
        when (this) {
            PdlPerson.Adressebeskyttelse.GraderingType.FORTROLIG -> Beskyttelsesbehov.FORTROLIG
            PdlPerson.Adressebeskyttelse.GraderingType.STRENGT_FORTROLIG -> Beskyttelsesbehov.STRENGT_FORTROLIG
            PdlPerson.Adressebeskyttelse.GraderingType.STRENGT_FORTROLIG_UTLAND -> Beskyttelsesbehov.STRENGT_FORTROLIG_UTLAND
            else -> null
        }

    fun PdlPerson.Sikkerhetstiltak.mapToSikkerhetstiltak(): Sikkerhetstiltak? =
        Sikkerhetstiltak(
            tiltakstype = Sikkerhetstiltak.Tiltakstype.valueOf(this.tiltakstype.name),
            beskrivelse = this.beskrivelse,
            gyldigFraOgMed = this.gyldigFraOgMed,
            gyldigTilOgMed = this.gyldigTilOgMed
        )
}
