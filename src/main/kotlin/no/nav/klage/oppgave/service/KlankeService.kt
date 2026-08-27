package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.HandledInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakAssignedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.clients.klageunleashproxy.KlageUnleashProxyClient
import no.nav.klage.oppgave.clients.klanke.KlankeClient
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class KlankeService(
    private val klankeClient: KlankeClient,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val klageUnleashProxyClient: KlageUnleashProxyClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private const val USE_NEW_KLANKE = "use-new-klanke"
    }

    private fun useNewKlanke(navIdent: String): Boolean {
        val enabled = klageUnleashProxyClient.isEnabled(feature = USE_NEW_KLANKE, navIdent = navIdent)
        if (enabled) {
            logger.debug("Using new-klanke for navident {}", navIdent)
        }
        return enabled
    }

    fun getSakWithAppAccess(
        sakId: String,
        input: GetSakAppAccessInput,
    ): SakFromKlanke =
        if (useNewKlanke(navIdent = input.saksbehandlerIdent)) {
            klankeClient.getSakWithAppAccess(sakId = sakId, input = input)
        } else {
            klageFssProxyClient.getSakWithAppAccess(sakId = sakId, input = input)
        }

    fun setToHandledInKabal(
        sakId: String,
        input: HandledInKabalInput,
    ) {
        if (useNewKlanke(navIdent = tokenUtil.getIdent())) {
            klankeClient.setToHandledInKabal(sakId = sakId, input = input)
        } else {
            klageFssProxyClient.setToHandledInKabal(sakId = sakId, input = input)
        }
    }

    fun setToFinishedWithAppAccess(
        sakId: String,
        input: SakFinishedInput,
    ) {
        if (useNewKlanke(navIdent = input.saksbehandlerIdent)) {
            klankeClient.setToFinishedWithAppAccess(sakId = sakId, input = input)
        } else {
            klageFssProxyClient.setToFinishedWithAppAccess(sakId = sakId, input = input)
        }
    }

    fun setToAssigned(
        sakId: String,
        input: SakAssignedInput,
    ) {
        if (useNewKlanke(navIdent = input.saksbehandlerIdent)) {
            klankeClient.setToAssigned(sakId = sakId, input = input)
        } else {
            klageFssProxyClient.setToAssigned(sakId = sakId, input = input)
        }
    }

    fun setToFeilregistrertInKabal(
        sakId: String,
        input: FeilregistrertInKabalInput,
    ) {
        if (useNewKlanke(navIdent = input.saksbehandlerIdent)) {
            klankeClient.setToFeilregistrertInKabal(sakId = sakId, input = input)
        } else {
            klageFssProxyClient.setToFeilregistrertInKabal(sakId = sakId, input = input)
        }
    }
}
