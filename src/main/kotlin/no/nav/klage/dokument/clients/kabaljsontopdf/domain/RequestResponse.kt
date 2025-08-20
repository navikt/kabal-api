package no.nav.klage.dokument.clients.kabaljsontopdf.domain

import java.time.LocalDate

data class DocumentValidationResponse(
    val errors: List<DocumentValidationError> = emptyList()
) {
    data class DocumentValidationError(
        val type: String,
        val paths: List<List<Int>> = emptyList()
    )
}

data class InnholdsfortegnelseRequest(
    val parentTitle: String,
    val parentDate: LocalDate,
    val documents: List<Document>,
) {
    data class Document(
        val tittel: String,
        val tema: String,
        val dato: LocalDate,
        val avsenderMottaker: String,
        val saksnummer: String,
        val type: Type,
    ) {
        enum class Type {
            I,
            U,
            N,
        }
    }
}

data class SvarbrevRequest(
    val title: String,
    val sakenGjelder: Part,
    val klager: Part?,
    val ytelseId: String,
    val fullmektigFritekst: String?,
    val receivedDate: LocalDate,
    val behandlingstidUnits: Int,
    val behandlingstidUnitTypeId: String,
    val avsenderEnhetId: String,
    val type: Type,
    val initialCustomText: String?,
    val customText: String?,
) {
    data class Part(
        val name: String,
        val fnr: String,
    )

    enum class Type {
        KLAGE,
        ANKE,
        OMGJOERINGSKRAV
    }
}

data class ForlengetBehandlingstidRequest(
    val title: String,
    val sakenGjelder: Part,
    val klager: Part?,
    val fullmektigFritekst: String?,
    val ytelseId: String,
    val mottattKlageinstans: LocalDate,
    val previousBehandlingstidInfo: String?,
    val reason: String?,
    val behandlingstidUnits: Int?,
    val behandlingstidUnitTypeId: String?,
    val behandlingstidDate: LocalDate?,
    val avsenderEnhetId: String,
    val type: Type,
    val customText: String?,
) {
    data class Part(
        val name: String,
        val fnr: String,
    )

    enum class Type {
        KLAGE,
        ANKE,
        OMGJOERINGSKRAV
    }
}