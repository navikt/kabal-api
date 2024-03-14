package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.TildelingEvent
import no.nav.klage.oppgave.api.view.WithPrevious
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.HistoryService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger")
class BehandlingOppgaveController(
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val historyService: HistoryService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{behandlingId}/fradelingreason")
    fun getFradelingReason(
        @PathVariable("behandlingId") behandlingId: UUID
    ): WithPrevious<TildelingEvent>? {
        logBehandlingMethodDetails(
            ::getFradelingReason.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId)

        val tildelingHistory = historyService.createTildelingHistory(
            tildelingHistorikkSet = behandling.tildelingHistorikk,
            behandlingCreated = behandling.created,
            originalHjemmelIdList = behandling.hjemler.joinToString(",")
        )

        return if (behandling.tildeling == null) {
            val fradelingerBySaksbehandler = tildelingHistory.filter {
                it.event?.fradelingReasonId != null && it.previous.event?.saksbehandler?.navIdent == innloggetSaksbehandlerService.getInnloggetIdent()
            }

            if (fradelingerBySaksbehandler.isNotEmpty()) {
                //return most recent fradeling if there are multiple
                fradelingerBySaksbehandler.first()
            } else {
                null
            }
        } else null
    }
}