package no.nav.klage.oppgave.clients.pdl.graphql

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class HentIdenterResponse(val data: HentIdenterDataWrapper?, val errors: List<PdlError>? = null)

data class HentIdenterDataWrapper(val hentIdenter: Identer)

data class Identer(
    val identer: List<Ident>,
)

data class Ident(
    val ident: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HentPersonResponse(val data: PdlPersonDataWrapper?, val errors: List<PdlError>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HentPersonBolkResponse(val data: PdlPersonBolkDataWrapper?, val errors: List<PdlError>? = null)

data class PdlPersonDataWrapper(val hentPerson: PdlPerson?)

data class PdlPersonBolkDataWrapper(val hentPersonBolk: List<HentPersonBolkResult>?)

data class HentPersonBolkResult(
    val ident: String,
    val person: PdlPerson?,
    val code: String,
)

data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val navn: List<Navn>,
    val kjoenn: List<Kjoenn>,
    val vergemaalEllerFremtidsfullmakt: List<VergemaalEllerFremtidsfullmakt>,
    val doedsfall: List<Doedsfall>,
    val sikkerhetstiltak: List<Sikkerhetstiltak>
) {
    data class Adressebeskyttelse(val gradering: GraderingType) {
        enum class GraderingType { STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, FORTROLIG, UGRADERT }
    }

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val metadata: Metadata,
    ) {
        data class Metadata(val master: Master) {
            enum class Master { PDL, FREG }
        }
    }

    data class Kjoenn(val kjoenn: KjoennType?) {
        enum class KjoennType { MANN, KVINNE, UKJENT }
    }

    data class VergemaalEllerFremtidsfullmakt(
        val type: String,
        val embete: String,
        val vergeEllerFullmektig: VergeEllerFullmektig
    ) {
        data class VergeEllerFullmektig(
            val motpartsPersonident: String,
            val omfang: String?,
            val omfangetErInnenPersonligOmraad: Boolean?
        )
    }

    data class Doedsfall(
        val doedsdato: LocalDate,
    )

    data class Sikkerhetstiltak(
        val tiltakstype: Tiltakstype,
        val beskrivelse: String,
        val gyldigFraOgMed: LocalDate,
        val gyldigTilOgMed: LocalDate,
    )

    enum class Tiltakstype {
        FYUS,
        TFUS,
        FTUS,
        DIUS,
        TOAN,
    }
}
