package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class ForlengetBehandlingstidService {

    fun setTitle(behandlingId: UUID, input: ForlengetBehandlingstidTitleInput) {
        TODO("Not yet implemented")
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