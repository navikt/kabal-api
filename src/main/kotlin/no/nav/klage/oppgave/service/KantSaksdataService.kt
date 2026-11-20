package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.kant.KantApiGateway
import no.nav.klage.oppgave.domain.behandling.BehandlingWithKantReference
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class KantSaksdataService(
    private val behandlingRepository: BehandlingRepository,
    private val kantApiGateway: KantApiGateway,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Transactional
    fun finalizeSaksdata(behandlingId: UUID) {
        val behandling = checkNotNull(behandlingRepository.getReferenceById(behandlingId)) { "Behandling not found" }
        check(behandling.feilregistrering == null) { "Cannot finalize Kant for feilregistrert behandling" }
        check(behandling is BehandlingWithKantReference) { "Behandling does not support Kant" }
        kantApiGateway.finalizeSaksdata(behandling)
    }
}
