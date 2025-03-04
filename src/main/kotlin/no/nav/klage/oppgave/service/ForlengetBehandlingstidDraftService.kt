package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.klage.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.domain.klage.VarsletBehandlingstid
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class ForlengetBehandlingstidDraftService(
    private val behandlingService: BehandlingService,
) {

    fun getForlengetBehandlingstidDraft(behandlingId: UUID): ForlengetBehandlingstidDraftView {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)
        if (behandling is BehandlingWithVarsletBehandlingstid) {
            return if (behandling.forlengetBehandlingstidDraft == null) {
                ForlengetBehandlingstidDraft(behandlingstid = VarsletBehandlingstid()).toView()
            } else {
                behandling.forlengetBehandlingstidDraft!!.toView()
            }
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }
    }

    fun setTitle(behandlingId: UUID, input: ForlengetBehandlingstidTitleInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.title = input.title
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setFullmektigFritekst(behandlingId: UUID, input: ForlengetBehandlingstidFullmektigFritekstInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = input.fullmektigFritekst
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setCustomText(behandlingId: UUID, input: ForlengetBehandlingstidCustomTextInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.customText = input.customText
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setReason(behandlingId: UUID, input: ForlengetBehandlingstidReasonInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.reason = input.reason
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setBehandlingstidUnits(behandlingId: UUID, input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid!!.varsletBehandlingstidUnits = input.varsletBehandlingstidUnits
        setVarsletFristBasedOnUnits(behandling.varsletBehandlingstid!!)
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setBehandlingstidUnitTypeId(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid!!.varsletBehandlingstidUnitType = TimeUnitType.of(input.varsletBehandlingstidUnitTypeId)
        setVarsletFristBasedOnUnits(behandling.varsletBehandlingstid!!)
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setBehandlingstidDate(behandlingId: UUID, input: ForlengetBehandlingstidBehandlingstidDateInput): ForlengetBehandlingstidDraftView {
        val behandling = getBehandlingWithForlengetBehandlingstidDraft(behandlingId = behandlingId)
        behandling.forlengetBehandlingstidDraft!!.behandlingstid!!.varsletFrist = input.behandlingstidDate
        behandling.forlengetBehandlingstidDraft!!.behandlingstid!!.varsletBehandlingstidUnits = null
        behandling.forlengetBehandlingstidDraft!!.behandlingstid!!.varsletBehandlingstidUnitType = null
        return behandling.forlengetBehandlingstidDraft!!.toView()
    }

    fun setReceivers(behandlingId: UUID, input: ForlengetBehandlingstidReceiversInput): ForlengetBehandlingstidDraftView {
        TODO("Not yet implemented")
    }

    private fun setVarsletFristBasedOnUnits(varsletBehandlingstid: VarsletBehandlingstid) {
        if (varsletBehandlingstid.varsletBehandlingstidUnits != null && varsletBehandlingstid.varsletBehandlingstidUnitType != null) {
            //Her velger vi å ta utgangspunkt i dagens dato. Ta det opp med funksjonell når vi har en demo.
            varsletBehandlingstid.varsletFrist = when (varsletBehandlingstid.varsletBehandlingstidUnitType!!) {
                TimeUnitType.WEEKS -> LocalDate.now().plusWeeks(varsletBehandlingstid.varsletBehandlingstidUnits!!.toLong())
                TimeUnitType.MONTHS -> LocalDate.now().plusMonths(varsletBehandlingstid.varsletBehandlingstidUnits!!.toLong())
            }
        }
    }

    private fun getBehandlingWithForlengetBehandlingstidDraft(behandlingId: UUID): BehandlingWithVarsletBehandlingstid {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)
        if (behandling is BehandlingWithVarsletBehandlingstid) {
            if (behandling.forlengetBehandlingstidDraft == null) {
                behandling.forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft(behandlingstid = VarsletBehandlingstid())
            }
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }
        return behandling
    }

    private fun ForlengetBehandlingstidDraft.toView(): ForlengetBehandlingstidDraftView {
        return ForlengetBehandlingstidDraftView(
            title = title,
            fullmektigFritekst = fullmektigFritekst,
            customText = customText,
            reason = reason,
            behandlingstid = behandlingstid!!.toView(),
        )
    }

    private fun VarsletBehandlingstid.toView(): ForlengetBehandlingstidVarsletBehandlingstidView {
        return ForlengetBehandlingstidVarsletBehandlingstidView(
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitType?.id,
            varsletFrist = varsletFrist,
        )
    }
}