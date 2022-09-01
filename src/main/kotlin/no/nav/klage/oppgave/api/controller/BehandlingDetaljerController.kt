package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
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
@RequestMapping("/klagebehandlinger")
//TODO: Flytte dette inn i BehandlingController?
class BehandlingDetaljerController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val auditLogger: AuditLogger
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/detaljer")
    fun getBehandlingDetaljer(
        @PathVariable("id") behandlingId: UUID
    ): BehandlingDetaljerView {
        logBehandlingMethodDetails(
            ::getBehandlingDetaljer.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        return behandlingMapper.mapBehandlingToBehandlingDetaljerView(
            behandlingService.getBehandling(behandlingId)
        ).also {
            auditLogger.log(
                AuditLogEvent(
                    navIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                    personFnr = it.sakenGjelder.person?.foedselsnummer
                )
            )
        }
    }
}