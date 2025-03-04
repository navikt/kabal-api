package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.BehandlingWithVarsletBehandlingstid
import no.nav.klage.oppgave.domain.klage.ForlengetBehandlingstidDraft
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class ForlengetBehandlingstidService(
    private val behandlingService: BehandlingService,
) {

    fun setTitle(behandlingId: UUID, input: ForlengetBehandlingstidTitleInput) {
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId = behandlingId)
        if (behandling is BehandlingWithVarsletBehandlingstid) {
            if (behandling.forlengetBehandlingstidDraft == null) {
                behandling.forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
            }
            behandling.forlengetBehandlingstidDraft!!.title = input.title
        } else {
            error("Behandling har ikke varslet behandlingstid")
        }
    }

    fun setFullmektigFritekst(behandlingId: UUID, input: ForlengetBehandlingstidFullmektigFritekstInput) {
        TODO("Not yet implemented")
    }

    fun setCustomText(behandlingId: UUID, input: ForlengetBehandlingstidCustomTextInput) {
        TODO("Not yet implemented")
    }

    fun setReason(behandlingId: UUID, input: ForlengetBehandlingstidCustomTextInput) {
        TODO("Not yet implemented")
    }

    fun setBehandlingstidUnits(behandlingId: UUID, input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput) {
        TODO("Not yet implemented")
    }

    fun setBehandlingstidUnitTypeId(
        behandlingId: UUID,
        input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ) {
        TODO("Not yet implemented")
    }

    fun setBehandlingstidDate(behandlingId: UUID, input: ForlengetBehandlingstidBehandlingstidDateInput) {
        TODO("Not yet implemented")
    }

    fun setReceivers(behandlingId: UUID, input: ForlengetBehandlingstidReceiversInput) {
        TODO("Not yet implemented")
    }
}