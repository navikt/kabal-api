package no.nav.klage.oppgave.api.view

import no.nav.klage.dokument.api.view.HandlingEnum
import java.time.LocalDateTime

data class ForlengetBehandlingstidTitleInput(val title: String)

data class ForlengetBehandlingstidFullmektigFritekstInput(val fullmektigFritekst: String?)

data class ForlengetBehandlingstidCustomTextInput(val customText: String)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitsInput(val varsletBehandlingstidUnits: Int)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput(val varsletBehandlingstidUnitTypeId: String?)

data class ForlengetBehandlingstidBehandlingstidDateInput(val behandlingstidDate: LocalDateTime?)

data class ForlengetBehandlingstidReceiversInput(
    val receivers: List<ForlengetBehandlingstidReceiverInput>,
)

data class ForlengetBehandlingstidReceiverInput(
    val id: String?,
    val handling: HandlingEnum,
    val overriddenAddress: ForlengetBehandlingstidAddressInput?,
    val navn: String?,
) {
    data class ForlengetBehandlingstidAddressInput(
        val adresselinje1: String?,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val landkode: String,
        val postnummer: String?,
    )
}