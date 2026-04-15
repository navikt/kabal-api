package no.nav.klage.oppgave.clients.nom

import java.time.LocalDate

data class GetAnsattResponse(val data: GetAnsattDataWrapper?, val errors: List<NomError>? = null) {
    override fun toString(): String {
        return "GetAnsattResponse(data=$data, errors=$errors)"
    }
}

data class GetAnsatteResponse(val data: GetAnsatteDataWrapper?, val errors: List<NomError>? = null) {
    override fun toString(): String {
        return "GetAnsatteResponse(data=$data, errors=$errors)"
    }
}

data class GetAnsattDataWrapper(val ressurs: Ansatt?) {
    override fun toString(): String {
        return "DataWrapper(ressurs=$ressurs)"
    }
}

data class GetAnsatteDataWrapper(val ressurser: List<Ressurs>) {
    override fun toString(): String {
        return "DataWrapper(ressurser=$ressurser)"
    }
}

data class Ressurs(
    val id: String,
    val ressurs: Ansatt?,
)

data class Ansatt(
    val navident: String,
    val sluttdato: LocalDate?,
) {
    override fun toString(): String {
        return "Ansatt(navident='$navident', sluttdato=$sluttdato)"
    }
}

data class NomError(
    val message: String,
    val locations: List<NomErrorLocation>,
    val path: List<String>?,
    val extensions: NomErrorExtension
)

data class NomErrorLocation(
    val line: Int?,
    val column: Int?
)

data class NomErrorExtension(
    val code: String?,
    val classification: String
)
