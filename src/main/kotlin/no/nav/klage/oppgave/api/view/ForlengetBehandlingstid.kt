package no.nav.klage.oppgave.api.view

import java.time.LocalDate

data class ForlengetBehandlingstidTitleInput(val title: String)

data class ForlengetBehandlingstidFullmektigFritekstInput(val fullmektigFritekst: String?)

data class ForlengetBehandlingstidCustomTextInput(val customText: String?)

data class ForlengetBehandlingstidReasonInput(val reason: String?)

data class ForlengetBehandlingstidPreviousBehandlingstidInfoInput(val previousBehandlingstidInfo: String?)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitsInput(val varsletBehandlingstidUnits: Int)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput(val varsletBehandlingstidUnitTypeId: String)

data class ForlengetBehandlingstidBehandlingstidDateInput(val behandlingstidDate: LocalDate?)

//data class ForlengetBehandlingstidReceiversInput(
//    val receivers: List<ForlengetBehandlingstidReceiverInput>,
//)

//data class ForlengetBehandlingstidReceiverInput(
//    val identifikator: String?,
//    val handling: HandlingEnum,
//    val overriddenAddress: AddressInput?,
//    val navn: String?,
//)

data class ForlengetBehandlingstidDraftView(
    val title: String?,
    val fullmektigFritekst: String?,
    val customText: String?,
    val reason: String?,
    val previousBehandlingstidInfo: String?,
    val behandlingstid: ForlengetBehandlingstidVarsletBehandlingstidView,
//    val receivers: List<ForlengetBehandlingstidReceiverView>,
)

data class ForlengetBehandlingstidVarsletBehandlingstidView(
    val varsletBehandlingstidUnits: Int?,
    val varsletBehandlingstidUnitTypeId: String?,
    val varsletFrist: LocalDate?,
)