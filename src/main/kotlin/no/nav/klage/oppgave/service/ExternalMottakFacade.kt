package no.nav.klage.oppgave.service

import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class ExternalMottakFacade(
    private val mottakService: MottakService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3) {
        val behandling = mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)

        tryToSendSvarbrev(behandling, hindreAutomatiskSvarbrev = oversendtKlageAnke.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageV2(oversendtKlage: OversendtKlageV2) {
        val behandling = mottakService.createMottakForKlageV2(oversendtKlage)

        tryToSendSvarbrev(behandling, hindreAutomatiskSvarbrev = oversendtKlage.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageAnkeV3ForE2ETests(oversendtKlageAnke: OversendtKlageAnkeV3): Behandling {
        val behandling = mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)

        tryToSendSvarbrev(behandling, hindreAutomatiskSvarbrev = oversendtKlageAnke.hindreAutomatiskSvarbrev == true)

        return behandling
    }

    private fun tryToSendSvarbrev(
        behandling: Behandling,
        hindreAutomatiskSvarbrev: Boolean
    ) {
        try {
            dokumentUnderArbeidService.sendSvarbrev(
                behandling = behandling,
                hindreAutomatiskSvarbrev = hindreAutomatiskSvarbrev,
            )
        } catch (e: Exception) {
            mottakService.createTaskForMerkantil(
                behandlingId = behandling.id,
                reason = e.message ?: "Ukjent feil"
            )
        }
    }

}