package no.nav.klage.oppgave.api.view

import no.nav.klage.dokument.api.view.DokumentView
import java.time.LocalDate

data class ForlengetBehandlingstidTitleInput(val title: String)

data class ForlengetBehandlingstidFullmektigFritekstInput(val fullmektigFritekst: String?)

data class ForlengetBehandlingstidCustomTextInput(val customText: String?)

data class ForlengetBehandlingstidReasonInput(val reason: String?)

data class ForlengetBehandlingstidPreviousBehandlingstidInfoInput(val previousBehandlingstidInfo: String?)

data class ForlengetBehandlingstidReasonNoLetterInput(val reasonNoLetter: String?)

data class ForlengetBehandlingstidDoNotSendLetterInput(val doNotSendLetter: Boolean)

data class ForlengetBehandlingstidVarselTypeIsOriginal(val varselTypeIsOriginal: Boolean)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitsInput(val varsletBehandlingstidUnits: Int)

data class ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput(val varsletBehandlingstidUnitTypeId: String)

data class ForlengetBehandlingstidBehandlingstidDateInput(val behandlingstidDate: LocalDate?)

data class ForlengetBehandlingstidDraftView(
    val title: String?,
    val fullmektigFritekst: String?,
    val customText: String?,
    val reason: String?,
    val previousBehandlingstidInfo: String?,
    val behandlingstid: ForlengetBehandlingstidVarsletBehandlingstidView,
    val reasonNoLetter: String?,
    val doNotSendLetter: Boolean,
    val varselTypeIsOriginal: Boolean,
    val receivers: List<DokumentView.Mottaker>,
    val timesPreviouslyExtended: Int,
)

data class ForlengetBehandlingstidVarsletBehandlingstidView(
    val varsletBehandlingstidUnits: Int?,
    val varsletBehandlingstidUnitTypeId: String,
    val varsletFrist: LocalDate?,
    val calculatedFrist: LocalDate?,
)