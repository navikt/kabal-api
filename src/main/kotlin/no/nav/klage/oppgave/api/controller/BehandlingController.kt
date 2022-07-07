package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
class BehandlingController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/klagebehandlinger/{behandlingId}/sattpaavent")
    fun setSattPaaVent(
        @ApiParam(value = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setSattPaaVent.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.setSattPaaVent(
            behandlingId = behandlingId,
            setNull = false,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return BehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/klagebehandlinger/{behandlingId}/sattpaavent")
    fun deleteSattPaaVent(
        @ApiParam(value = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::deleteSattPaaVent.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.setSattPaaVent(
            behandlingId = behandlingId,
            setNull = true,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return BehandlingEditedView(modified = modified)
    }

    @PostMapping("/behandlinger/{id}/fullfoer")
    fun fullfoerBehandling(
        @PathVariable("id") behandlingId: UUID
    ): BehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            ::fullfoerBehandling.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val klagebehandling = behandlingService.ferdigstillBehandling(
            behandlingId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return behandlingMapper.mapToBehandlingFullfoertView(klagebehandling)
    }


    @PutMapping("/behandlinger/{id}/mottattklageinstans")
    fun setMottattKlageinstans(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setMottattKlageinstans.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setMottattKlageinstans(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/behandlinger/{id}/mottattvedtaksinstans")
    fun setMottattVedtaksinstans(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setMottattVedtaksinstans.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setMottattVedtaksinstans(
            behandlingId = behandlingId,
            date = input.date,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/behandlinger/{id}/sendttiltrygderetten")
    fun setSendtTilTrygderetten(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setSendtTilTrygderetten.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setSendtTilTrygderetten(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/behandlinger/{id}/kjennelsemottatt")
    fun setKjennelseMottatt(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setKjennelseMottatt.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setKjennelseMottatt(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    /**
     * Valgfri validering før innsending/fullføring.
     * Gjøres uansett ved fullføring av behandlingen.
     */
    @GetMapping("/behandlinger/{id}/validate")
    fun validate(
        @PathVariable("id") behandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validate.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        behandlingService.validateBehandlingBeforeFinalize(
            behandlingService.getBehandling(
                behandlingId
            )
        )
        return ValidationPassedResponse()
    }

    @PutMapping("/behandlinger/{id}/innsendingshjemler")
    fun setInnsendingshjemler(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: InnsendingshjemlerInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setInnsendingshjemler.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setInnsendingshjemler(
            behandlingId = behandlingId,
            hjemler = input.hjemler,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }
}