package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.KlagebehandlingDetaljerView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.AuditLogger
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
//TODO: Flytte dette inn i KlagebehandlingController?
class KlagebehandlingDetaljerController(
    private val klagebehandlingService: KlagebehandlingService,
    private val klagebehandlingMapper: KlagebehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val auditLogger: AuditLogger
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/detaljer")
    fun getKlagebehandlingDetaljer(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingDetaljerView {
        logKlagebehandlingMethodDetails(
            "getKlagebehandlingDetaljer",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )

        //TODO Remove when all klagebehandlinger have kakaKvalitetsvurderingId
        klagebehandlingService.createAndStoreKakaKvalitetsvurderingIdIfMissing(klagebehandlingId)

        return klagebehandlingMapper.mapKlagebehandlingToKlagebehandlingDetaljerView(
            klagebehandlingService.getKlagebehandling(klagebehandlingId)
        ).also {
            auditLogger.log(
                AuditLogEvent(
                    navIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                    personFnr = it.sakenGjelderFoedselsnummer
                )
            )
        }
    }
}