package no.nav.klage.oppgave.clients.nom

import java.time.LocalDate

data class GetAnsattResponse(val data: DataWrapper?, val errors: List<NomError>? = null) {
    override fun toString(): String {
        return "GetAnsattResponse(data=$data, errors=$errors)"
    }
}

data class DataWrapper(val ressurs: Ansatt?) {
    override fun toString(): String {
        return "DataWrapper(ressurs=$ressurs)"
    }
}

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
