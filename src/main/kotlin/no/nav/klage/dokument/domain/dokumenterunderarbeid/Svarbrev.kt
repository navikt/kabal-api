package no.nav.klage.dokument.domain.dokumenterunderarbeid

import no.nav.klage.kodeverk.Type

data class Svarbrev(
    val title: String,
    val receivers: List<Receiver>,
    val fullmektigFritekst: String?,
    val varsletBehandlingstidWeeks: Int,
    val type: Type,
    val customText: String?
) {

    data class Receiver(
        val id: String,
        val handling: HandlingEnum,
        val overriddenAddress: AddressInput?,
    ) {
        data class AddressInput(
            val adresselinje1: String?,
            val adresselinje2: String?,
            val adresselinje3: String?,
            val landkode: String,
            val postnummer: String?,
        )

        enum class HandlingEnum {
            AUTO,
            LOCAL_PRINT,
            CENTRAL_PRINT
        }
    }
}