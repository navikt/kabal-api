package no.nav.klage.oppgave.service

import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.klageenheter
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.clients.kabalinnstillinger.KabalInnstillingerClient
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ExternalMottakFacade(
    private val mottakService: MottakService,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val behandlingService: BehandlingService,
    private val saksbehandlerService: SaksbehandlerService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val kabalInnstillingerClient: KabalInnstillingerClient,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3) {
        val behandling = mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)

        if (oversendtKlageAnke.saksbehandlerIdent != null) {
            tryToSetSaksbehandler(behandling = behandling, saksbehandlerIdent = oversendtKlageAnke.saksbehandlerIdent)
        }

        tryToSendSvarbrev(behandling, hindreAutomatiskSvarbrev = oversendtKlageAnke.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageV2(oversendtKlage: OversendtKlageV2) {
        val behandling = mottakService.createMottakForKlageV2(oversendtKlage)

        tryToSendSvarbrev(behandling, hindreAutomatiskSvarbrev = oversendtKlage.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageAnkeV3ForE2ETests(oversendtKlageAnke: OversendtKlageAnkeV3): Behandling {
        val behandling = mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)

        if (oversendtKlageAnke.saksbehandlerIdent != null) {
            tryToSetSaksbehandler(behandling = behandling, saksbehandlerIdent = oversendtKlageAnke.saksbehandlerIdent)
        }

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

    private fun tryToSetSaksbehandler(
        behandling: Behandling,
        saksbehandlerIdent: String
    ) {
        try {
            setSaksbehandler(
                behandling = behandling,
                saksbehandlerIdent = saksbehandlerIdent,
            )
        } catch (e: Exception) {
            logger.error("Klarte ikke å tildele behandling ${behandling.id} til saksbehandlerIdent $saksbehandlerIdent. Feil: $e")
            mottakService.createTaskForMerkantil(
                behandlingId = behandling.id,
                reason = "Klarte ikke å tildele behandling ${behandling.id} til saksbehandlerIdent $saksbehandlerIdent. Feilmelding: ${e.message}"
            )
        }
    }

    private fun setSaksbehandler(behandling: Behandling, saksbehandlerIdent: String) {
        logger.debug("Preparing to set saksbehandler. Getting enhet for saksbehandler $saksbehandlerIdent")
        val enhetForSaksbehandler = try {
            saksbehandlerService.getEnhetForSaksbehandler(
                saksbehandlerIdent
            ).enhetId
        } catch (e: Exception) {
            logger.error(
                "Couldn't get enhet for saksbehandlerident {}, returning. Exception: {}",
                saksbehandlerIdent,
                e.message
            )
            return
        }

        val enhet = Enhet.entries.find {
            it.navn == enhetForSaksbehandler
        }

        if (enhet == null) {
            logger.error("Couldn't get enhet for saksbehandlerident {}, returning.", saksbehandlerIdent)
            return
        }

        if (enhet !in klageenheter) {
            logger.error("Enhet {} er ikke klageenhet. BehandlingId: {}", enhet.id, behandling.id)
            return
        }

        logger.debug("Found enhet {} for saksbehandlerid {}", enhet, saksbehandlerIdent)

        val saksbehandlerAccess =
            kabalInnstillingerClient.getSaksbehandlersTildelteYtelserAppAccess(navIdent = saksbehandlerIdent)

        if (saksbehandlerAccess.created == null) {
            logger.debug(
                "Saksbehandler {} mangler innstillinger i Kabal. BehandlingId: {}",
                saksbehandlerIdent,
                behandling.id
            )
        } else if (saksbehandlerAccess.ytelseIdList.none { it == behandling.ytelse.id }) {
            logger.debug(
                "Saksbehandler {} mangler tilgang til ytelse {} i Kabal. BehandlingId: {}",
                saksbehandlerIdent,
                behandling.ytelse.id,
                behandling.id
            )
        }

        behandlingService.setSaksbehandler(
            behandlingId = behandling.id,
            tildeltSaksbehandlerIdent = saksbehandlerIdent,
            enhetId = enhet.navn,
            fradelingReason = null,
            utfoerendeSaksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
        )
    }
}