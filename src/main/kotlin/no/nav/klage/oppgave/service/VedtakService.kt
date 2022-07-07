package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setHjemlerInVedtak
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setUtfallInVedtak
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VedtakService(
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun setUtfall(
        behandlingId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )
        val event =
            behandling.setUtfallInVedtak(utfall, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun setHjemler(
        behandlingId: UUID,
        hjemler: Set<Registreringshjemmel>,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId = behandlingId,
            systemUserContext = systemUserContext,
        )
        val event =
            behandling.setHjemlerInVedtak(hjemler, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }
}
