package no.nav.klage.oppgave.service.mapper

import no.nav.klage.oppgave.clients.pdl.graphql.PdlPerson
import no.nav.klage.oppgave.domain.person.Beskyttelsesbehov
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.domain.person.Sikkerhetstiltak

fun PdlPerson.toPerson(fnr: String): Person {
    val preferredName = if (navn.size == 1) {
        navn.first()
    } else {
        navn.firstOrNull { it.metadata.master == PdlPerson.Navn.Metadata.Master.PDL } ?: navn.first()
    }
    return Person(
        foedselsnr = fnr,
        fornavn = preferredName.fornavn,
        mellomnavn = preferredName.mellomnavn,
        etternavn = preferredName.etternavn,
        beskyttelsesbehov = adressebeskyttelse.firstOrNull()?.gradering?.mapToBeskyttelsesbehov(),
        kjoenn = kjoenn.firstOrNull()?.kjoenn?.name,
        vergemaalEllerFremtidsfullmakt = vergemaalEllerFremtidsfullmakt.isNotEmpty(),
        doed = doedsfall.firstOrNull()?.doedsdato,
        sikkerhetstiltak = sikkerhetstiltak.firstOrNull()?.mapToSikkerhetstiltak(),
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
