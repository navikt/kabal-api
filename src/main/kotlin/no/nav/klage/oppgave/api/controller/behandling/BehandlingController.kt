package no.nav.klage.oppgave.api.controller.behandling

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping(value = ["/klagebehandlinger", "/behandlinger"])
class BehandlingController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{behandlingId}/sattpaavent")
    fun setSattPaaVent(
        @Parameter(description = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setSattPaaVent.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.setSattPaaVent(
            behandlingId = behandlingId,
            setNull = false,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return BehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/{behandlingId}/sattpaavent")
    fun deleteSattPaaVent(
        @Parameter(description = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::deleteSattPaaVent.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.setSattPaaVent(
            behandlingId = behandlingId,
            setNull = true,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return BehandlingEditedView(modified = modified)
    }

    @PostMapping("/{behanldingId}/fullfoer")
    fun fullfoerBehandling(
        @PathVariable("behandlingId") behandlingId: UUID
    ): BehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            ::fullfoerBehandling.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val klagebehandling = behandlingService.ferdigstillBehandling(
            behandlingId,
            innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return behandlingMapper.mapToBehandlingFullfoertView(klagebehandling)
    }


    @PutMapping("/{behandlingId}/mottattklageinstans")
    fun setMottattKlageinstans(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setMottattKlageinstans.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setMottattKlageinstans(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/mottattvedtaksinstans")
    fun setMottattVedtaksinstans(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setMottattVedtaksinstans.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setMottattVedtaksinstans(
            behandlingId = behandlingId,
            date = input.date,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/sendttiltrygderetten")
    fun setSendtTilTrygderetten(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setSendtTilTrygderetten.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setSendtTilTrygderetten(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/kjennelsemottatt")
    fun setKjennelseMottatt(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setKjennelseMottatt.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setKjennelseMottatt(
            behandlingId = behandlingId,
            date = input.date.atStartOfDay(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/frist")
    fun setFrist(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setFrist.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setFrist(
            behandlingId = behandlingId,
            frist = input.date,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    /**
     * Valgfri validering før innsending/fullføring.
     * Gjøres uansett ved fullføring av behandlingen.
     */
    @GetMapping("/{behandlingId}/validate")
    fun validate(
        @PathVariable("behandlingId") behandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validate.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
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

    @PutMapping("/{behandlingId}/innsendingshjemler")
    fun setInnsendingshjemler(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: InnsendingshjemlerInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setInnsendingshjemler.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setInnsendingshjemler(
            behandlingId = behandlingId,
            hjemler = input.hjemler,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/fullmektig")
    fun setFullmektig(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: FullmektigInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setFullmektig.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setFullmektig(
            behandlingId = behandlingId,
            identifikator = input.identifikator,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @GetMapping("/{behandlingId}/potentialsaksbehandlere")
    fun getPotentialSaksbehandlere(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Saksbehandlere {
        logMethodDetails(
            ::getPotentialSaksbehandlere.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingService.getPotentialSaksbehandlereForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/potentialmedunderskrivere")
    fun getPotentialMedunderskrivere(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Medunderskrivere {
        logMethodDetails(
            ::getPotentialMedunderskrivere.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingService.getPotentialMedunderskrivereForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/sakengjelder")
    fun getSakenGjelder(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): SakenGjelderWrapped {
        logMethodDetails(
            ::getSakenGjelder.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingMapper.toSakenGjelderWrapped(
            behandlingService.getBehandling(behandlingId).sakenGjelder
        )
    }
}