package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.mapper.KvalitetsvurderingMapper
import no.nav.klage.oppgave.api.view.KlagebehandlingEditedView
import no.nav.klage.oppgave.api.view.KlagebehandlingKvalitetsvurderingView
import no.nav.klage.oppgave.api.view.KvalitetsvurderingEditableFieldsInput
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.KvalitetsvurderingEditableFieldsFacade
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingKvalitetsvurderingController(
    private val klagebehandlingService: KlagebehandlingService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val kvalitetsvurderingMapper: KvalitetsvurderingMapper,
    private val klagebehandlingMapper: KlagebehandlingMapper,
    private val kvalitetsvurderingEditableFieldsFacade: KvalitetsvurderingEditableFieldsFacade
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/kvalitetsvurdering")
    fun getKvalitetsvurdering(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingKvalitetsvurderingView {
        logKlagebehandlingMethodDetails("getKvalitetsvurdering", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId, logger)
        return kvalitetsvurderingMapper.mapKlagebehandlingToKvalitetsvurderingView(
            klagebehandlingService.getKlagebehandling(klagebehandlingId)
        )
    }

    @PutMapping("/{id}/kvalitetsvurdering/editerbare")
    fun putKvalitetsvurderingEditableFields(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: KvalitetsvurderingEditableFieldsInput
    ): KlagebehandlingEditedView {
        logKlagebehandlingMethodDetails("putKvalitetsvurderingEditableFields", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId, logger)
        return klagebehandlingMapper.mapKlagebehandlingToKlagebehandlingEditableFieldsView(
            kvalitetsvurderingEditableFieldsFacade.updateEditableFields(
                klagebehandlingId,
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }
}