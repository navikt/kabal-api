package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.klageenheter
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV4
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.clients.kabalinnstillinger.KabalInnstillingerClient
import no.nav.klage.oppgave.domain.events.AutomaticSvarbrevEvent
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.repositories.AutomaticSvarbrevEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class ExternalMottakFacade(
    private val mottakService: MottakService,
    private val behandlingService: BehandlingService,
    private val saksbehandlerService: SaksbehandlerService,
    private val kabalInnstillingerClient: KabalInnstillingerClient,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val automaticSvarbrevEventRepository: AutomaticSvarbrevEventRepository,
    private val taskListMerkantilService: TaskListMerkantilService,
    private val svarbrevSettingsService: SvarbrevSettingsService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createMottakForKlageV2(oversendtKlage: OversendtKlageV2) {
        val behandling = mottakService.createMottakForKlageV2(oversendtKlage)

        tryToSendSvarbrev(behandlingId = behandling.id, hindreAutomatiskSvarbrev = oversendtKlage.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageAnkeV3(oversendtKlageAnke: OversendtKlageAnkeV3) {
        val behandling = mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)

        if (oversendtKlageAnke.saksbehandlerIdent != null) {
            tryToSetSaksbehandler(behandling = behandling, saksbehandlerIdent = oversendtKlageAnke.saksbehandlerIdent)
        }

        tryToSendSvarbrev(behandlingId = behandling.id, hindreAutomatiskSvarbrev = oversendtKlageAnke.hindreAutomatiskSvarbrev == true)
    }

    fun createMottakForKlageAnkeV4(oversendtKlageAnke: OversendtKlageAnkeV4): Behandling {
        val behandling = mottakService.createMottakForKlageAnkeV4(oversendtKlageAnke)

        if (oversendtKlageAnke.saksbehandlerIdentForTildeling != null) {
            tryToSetSaksbehandler(
                behandling = behandling,
                saksbehandlerIdent = oversendtKlageAnke.saksbehandlerIdentForTildeling
            )
        }

        tryToSendSvarbrev(behandlingId = behandling.id, hindreAutomatiskSvarbrev = oversendtKlageAnke.hindreAutomatiskSvarbrev == true)

        return behandling
    }

    private fun tryToSendSvarbrev(
        behandlingId: UUID,
        hindreAutomatiskSvarbrev: Boolean
    ) {
        if (hindreAutomatiskSvarbrev) {
            logger.debug("hindreAutomatiskSvarbrev set to true, returning without sending svarbrev")
            return
        }

        val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId)

        val svarbrevSettingsForYtelseAndType = svarbrevSettingsService.getSvarbrevSettingsForYtelseAndType(
            ytelse = behandling.ytelse,
            type = behandling.type,
        )

        val shouldSendSvarbrev = svarbrevSettingsForYtelseAndType?.shouldSend ?: false

        if (shouldSendSvarbrev) {
            automaticSvarbrevEventRepository.save(
                AutomaticSvarbrevEvent(
                    status = AutomaticSvarbrevEvent.AutomaticSvarbrevStatus.NOT_HANDLED,
                    created = LocalDateTime.now(),
                    modified = LocalDateTime.now(),
                    behandlingId = behandlingId,
                    dokumentUnderArbeidId = null,
                    receiversAreSet = false,
                    documentIsMarkedAsFinished = false,
                    varsletFristIsSetInBehandling = false
                )
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
            taskListMerkantilService.createTaskForMerkantil(
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