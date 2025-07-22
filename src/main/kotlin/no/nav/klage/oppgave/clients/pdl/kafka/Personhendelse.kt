package no.nav.klage.oppgave.clients.pdl.kafka

import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

val GenericRecord.opplysningstype get(): String {
    return get("opplysningstype").toString()
}

val GenericRecord.fnr get(): String {
    return (get("personidenter") as GenericData.Array<*>)
        .map { it.toString() }
        .first { it.length == 11 }
}

val GenericRecord.isAdressebeskyttelse get(): Boolean {
    return opplysningstype == OPPLYSNINGSTYPE_ADRESSEBESKYTTELSE
}

val GenericRecord.isRelevantForOurCache get(): Boolean {
    return opplysningstype in listOf(
        OPPLYSNINGSTYPE_ADRESSEBESKYTTELSE,
        OPPLYSNINGSTYPE_NAVN,
        OPPLYSNINGSTYPE_KJOENN,
        OPPLYSNINGSTYPE_VERGEMAAL_ELLER_FREMTIDSFULLMAKT,
        OPPLYSNINGSTYPE_DOEDSFALL,
        OPPLYSNINGSTYPE_SIKKERHETSTILTAK,
    )
}

const val OPPLYSNINGSTYPE_ADRESSEBESKYTTELSE = "ADRESSEBESKYTTELSE_V1"
const val OPPLYSNINGSTYPE_NAVN = "NAVN_V1"
const val OPPLYSNINGSTYPE_KJOENN = "KJOENN_V1"
const val OPPLYSNINGSTYPE_VERGEMAAL_ELLER_FREMTIDSFULLMAKT = "VERGEMAAL_ELLER_FREMTIDSFULLMAKT_V1"
const val OPPLYSNINGSTYPE_DOEDSFALL = "DOEDSFALL_V1"
const val OPPLYSNINGSTYPE_SIKKERHETSTILTAK = "SIKKERHETSTILTAK_V1"