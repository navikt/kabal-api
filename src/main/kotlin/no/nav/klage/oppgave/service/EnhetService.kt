package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.mapper.toEnhetView
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class EnhetService(
    private val behandlingService: BehandlingService,
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val norg2Client: Norg2Client,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getAllRelevantYtelserForEnhet(enhet: String): List<String> {
        val enhetensTildelteYtelser = kabalInnstillingerService.getTildelteYtelserForEnhet(enhet)
        val enhetensBehandlingerYtelseSet =
            behandlingService.getAllBehandlingerForEnhet(enhet).map { it.ytelse }.toSet()

        return (enhetensTildelteYtelser + enhetensBehandlingerYtelseSet).sortedBy { it.navn }.map { it.id }
    }

    fun findEnheter(
        enhetsnr: String?,
        enhetsnavn: String?,
    ): List<EnhetView> {
        val enheter = norg2Client.fetchEnheter()
        if (enhetsnr.isNullOrBlank() && enhetsnavn.isNullOrBlank()) {
            return enheter.map { toEnhetView(enhet = it) }
        }

        return enheter.filter {
            (enhetsnr.isNullOrBlank() || it.enhetsnr.contains(enhetsnr)) &&
                    (enhetsnavn.isNullOrBlank() || it.navn.contains(enhetsnavn, ignoreCase = true))
        }.map { toEnhetView(enhet = it) }
    }
}