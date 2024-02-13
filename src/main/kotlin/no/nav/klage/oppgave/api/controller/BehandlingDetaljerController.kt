package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.AuditLogger
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
class BehandlingDetaljerController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val auditLogger: AuditLogger,
    private val dokDistKanalClient: DokDistKanalClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{behandlingId}/detaljer")
    fun getBehandlingDetaljer(
        @PathVariable("behandlingId") behandlingId: UUID
    ): BehandlingDetaljerView {
        logBehandlingMethodDetails(
            ::getBehandlingDetaljer.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        return behandlingMapper.mapBehandlingToBehandlingDetaljerView(
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        ).also {
            auditLogger.log(
                AuditLogEvent(
                    navIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                    personFnr = it.sakenGjelder.id,
                    message = "Hentet behandlingsdetaljer"
                )
            )
        }
    }

    @GetMapping("/{behandlingId}/distribusjonskanal/{mottakerId}")
    fun getDistribusjonskanal(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("mottakerId") mottakerId: String,
    ): DokDistKanalClient.BestemDistribusjonskanalResponse {
        logBehandlingMethodDetails(
            ::getDistribusjonskanal.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        return behandlingService.getDistribusjonskanal(
            behandlingId = behandlingId,
            mottakerId = mottakerId
        )
    }
}