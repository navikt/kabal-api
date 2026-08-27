package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.BehandlingDateInput
import no.nav.klage.oppgave.api.view.BehandlingDateNullableInput
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.BehandlingEditedView
import no.nav.klage.oppgave.api.view.BehandlingFullfoertView
import no.nav.klage.oppgave.api.view.ExtraUtfallEditedView
import no.nav.klage.oppgave.api.view.FeilregistreringInput
import no.nav.klage.oppgave.api.view.FeilregistreringResponse
import no.nav.klage.oppgave.api.view.ForsterketRettInput
import no.nav.klage.oppgave.api.view.FullmektigInput
import no.nav.klage.oppgave.api.view.GosysOppgaveEditedView
import no.nav.klage.oppgave.api.view.GosysOppgaveIdInput
import no.nav.klage.oppgave.api.view.GosysOppgaveInput
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.api.view.HistoryResponse
import no.nav.klage.oppgave.api.view.IdentifikatorInput
import no.nav.klage.oppgave.api.view.InnsendingshjemlerInput
import no.nav.klage.oppgave.api.view.RelevantBehandlingerResponse
import no.nav.klage.oppgave.api.view.Rols
import no.nav.klage.oppgave.api.view.SattPaaVentInput
import no.nav.klage.oppgave.api.view.TilbakekrevingInput
import no.nav.klage.oppgave.api.view.TildelingEvent
import no.nav.klage.oppgave.api.view.UrlView
import no.nav.klage.oppgave.api.view.UtfallEditedView
import no.nav.klage.oppgave.api.view.ValidationPassedResponse
import no.nav.klage.oppgave.api.view.VedtakExtraUtfallSetInput
import no.nav.klage.oppgave.api.view.VedtakHjemlerInput
import no.nav.klage.oppgave.api.view.VedtakUtfallInput
import no.nav.klage.oppgave.api.view.WithPrevious
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import java.util.UUID

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger")
class BehandlingController(
    private val behandlingService: BehandlingService,
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
        @RequestBody input: SattPaaVentInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setSattPaaVent.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setSattPaaVent(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                input = input,
            )
        return BehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/{behandlingId}/sattpaavent")
    fun deleteSattPaaVent(
        @Parameter(description = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::deleteSattPaaVent.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        val modified =
            behandlingService.setSattPaaVent(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                input = null,
            )
        return BehandlingEditedView(modified = modified)
    }

    @PostMapping("/{behandlingId}/fullfoer")
    fun fullfoerBehandling(
        @PathVariable("behandlingId") behandlingId: UUID,
        // change value name after testing
        @RequestParam(value = "nybehandling", required = false) nyBehandlingEtterTROpphevet: Boolean = false,
        @RequestBody(required = false) gosysOppgaveInput: GosysOppgaveInput?,
    ): BehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            methodName = ::fullfoerBehandling.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            klagebehandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.ferdigstillBehandling(
            behandlingId = behandlingId,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            gosysOppgaveInput = gosysOppgaveInput,
            nyBehandlingEtterTROpphevet = nyBehandlingEtterTROpphevet,
        )
    }

    @PutMapping("/{behandlingId}/mottattklageinstans")
    fun setMottattKlageinstans(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setMottattKlageinstans.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setMottattKlageinstans(
                behandlingId = behandlingId,
                date = input.date.atStartOfDay(),
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/mottattvedtaksinstans")
    fun setMottattVedtaksinstans(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setMottattVedtaksinstans.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setMottattVedtaksinstans(
                behandlingId = behandlingId,
                date = input.date,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/sendttiltrygderetten")
    fun setSendtTilTrygderetten(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setSendtTilTrygderetten.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setSendtTilTrygderetten(
                behandlingId = behandlingId,
                date = input.date.atStartOfDay(),
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/kjennelsemottatt")
    fun setKjennelseMottatt(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateNullableInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setKjennelseMottatt.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setKjennelseMottatt(
                behandlingId = behandlingId,
                date = input.date?.atStartOfDay(),
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/frist")
    fun setFrist(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setFrist.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setFrist(
                behandlingId = behandlingId,
                frist = input.date,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    /**
     * Valgfri validering før innsending/fullføring.
     * Gjøres uansett ved fullføring av behandlingen.
     */
    @GetMapping("/{behandlingId}/validate", "/{behandlingId}/validate/fullfoer")
    fun validate(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            methodName = ::validate.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            klagebehandlingId = behandlingId,
            logger = logger,
        )

        behandlingService.validateBehandlingBeforeFinalize(behandlingId = behandlingId, nyBehandlingEtterTROpphevet = false)
        return ValidationPassedResponse()
    }

    /**
     * Valgfri validering før feilregistrering.
     */
    @GetMapping("/{behandlingId}/validate/feilregistrer")
    fun validateFeilregistrering(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            methodName = ::validateFeilregistrering.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            klagebehandlingId = behandlingId,
            logger = logger,
        )

        behandlingService.validateFeilregistrering(behandlingId)
        return ValidationPassedResponse()
    }

    /**
     * Valgfri validering før ny ankebehandling.
     */
    @GetMapping(value = ["/{behandlingId}/validate/nyankebehandling", "/{behandlingId}/validate/nybehandlingfratrygderettbehandling"])
    fun validateAnkebehandling(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            methodName = ::validateAnkebehandling.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            klagebehandlingId = behandlingId,
            logger = logger,
        )

        behandlingService.validateTrygderettenbehandlingBeforeNyBehandling(behandlingId)
        return ValidationPassedResponse()
    }

    @PutMapping("/{behandlingId}/innsendingshjemler")
    fun setInnsendingshjemler(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: InnsendingshjemlerInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setInnsendingshjemler.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setInnsendingshjemler(
                behandlingId = behandlingId,
                hjemler = input.hjemmelIdList,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/fullmektig")
    fun setFullmektig(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: FullmektigInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setFullmektig.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setFullmektig(
                behandlingId = behandlingId,
                input = input,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/klager")
    fun setKlager(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: IdentifikatorInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setKlager.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setKlager(
                behandlingId = behandlingId,
                identifikator = input.identifikator,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/tilbakekreving")
    fun setTilbakekreving(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: TilbakekrevingInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setTilbakekreving.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setTilbakekreving(
                behandlingId = behandlingId,
                tilbakekreving = input.tilbakekreving,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/paaanketvedtaksdato")
    fun setPaaanketVedtaksdato(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingDateInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setPaaanketVedtaksdato.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setPaaanketVedtaksdato(
                behandlingId = behandlingId,
                paaanketVedtaksdato = input.date,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/forsterketrett")
    fun setForsterketRett(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: ForsterketRettInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setForsterketRett.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService.setForsterketRett(
                behandlingId = behandlingId,
                forsterketRett = input.forsterketRett,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            )

        return BehandlingEditedView(modified = modified)
    }

    @GetMapping("/{behandlingId}/potentialsaksbehandlere")
    fun getPotentialSaksbehandlere(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Saksbehandlere {
        logMethodDetails(
            methodName = ::getPotentialSaksbehandlere.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getPotentialSaksbehandlereForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/potentialmedunderskrivere")
    fun getPotentialMedunderskrivere(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Medunderskrivere {
        logMethodDetails(
            methodName = ::getPotentialMedunderskrivere.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getPotentialMedunderskrivereForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/potentialrol")
    fun getPotentialROL(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Rols {
        logMethodDetails(
            methodName = ::getPotentialROL.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getPotentialROLForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/sakengjelder")
    fun getSakenGjelder(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingDetaljerView.SakenGjelderView {
        logMethodDetails(
            methodName = ::getSakenGjelder.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getSakenGjelderView(behandlingId)
    }

    // TODO: Remove url without redirect
    @GetMapping(value = ["/{behandlingId}/ainntekt", "/{behandlingId}/ainntekt/redirect"])
    fun getAInntektRedirect(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ModelAndView {
        logMethodDetails(
            methodName = ::getAInntektRedirect.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return ModelAndView("redirect:" + behandlingService.getAInntektUrl(behandlingId))
    }

    // TODO: Remove url without redirect
    @GetMapping(value = ["/{behandlingId}/aaregister", "/{behandlingId}/aaregister/redirect"])
    fun getAARegisterRedirect(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ModelAndView {
        logMethodDetails(
            methodName = ::getAARegisterRedirect.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return ModelAndView("redirect:" + behandlingService.getAARegisterUrl(behandlingId))
    }

    @GetMapping("/{behandlingId}/ainntekt/url")
    fun getAInntektUrl(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): UrlView {
        logMethodDetails(
            methodName = ::getAInntektUrl.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return UrlView(
            url = behandlingService.getAInntektUrl(behandlingId),
        )
    }

    @GetMapping("/{behandlingId}/aaregister/url")
    fun getAARegisterUrl(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): UrlView {
        logMethodDetails(
            methodName = ::getAARegisterUrl.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return UrlView(
            url = behandlingService.getAARegisterUrl(behandlingId),
        )
    }

    @PostMapping("/{behandlingId}/feilregistrer")
    fun setBehandlingFeilregistrert(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: FeilregistreringInput,
    ): FeilregistreringResponse {
        logMethodDetails(
            methodName = ::setBehandlingFeilregistrert.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.feilregistrer(
            behandlingId = behandlingId,
            reason = input.reason,
            fagsystem = Fagsystem.KABAL,
        )
    }

    @PutMapping("/{behandlingId}/gosysoppgaveid")
    fun setGosysOppgaveId(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: GosysOppgaveIdInput,
    ): GosysOppgaveEditedView {
        logBehandlingMethodDetails(
            methodName = ::setGosysOppgaveId.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.setGosysOppgaveId(
            behandlingId = behandlingId,
            gosysOppgaveId = input.gosysOppgaveId,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @PutMapping("/{behandlingId}/resultat/utfall")
    fun setUtfall(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakUtfallInput,
    ): UtfallEditedView {
        logBehandlingMethodDetails(
            methodName = ::setUtfall.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.setUtfall(
            behandlingId = behandlingId,
            utfall = if (input.utfallId != null) Utfall.of(input.utfallId) else null,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @PutMapping("/{behandlingId}/resultat/extra-utfall-set")
    fun setUtfallSet(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakExtraUtfallSetInput,
    ): ExtraUtfallEditedView {
        logBehandlingMethodDetails(
            methodName = ::setUtfallSet.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.setExtraUtfallSet(
            behandlingId = behandlingId,
            extraUtfallSet = input.extraUtfallIdSet.map { Utfall.of(it) }.toSet(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @PutMapping("/{behandlingId}/resultat/hjemler")
    fun setRegistreringshjemler(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakHjemlerInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            methodName = ::setRegistreringshjemler.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        val modified =
            behandlingService
                .setRegistreringshjemler(
                    behandlingId = behandlingId,
                    registreringshjemler = input.hjemmelIdSet.map { Registreringshjemmel.of(it) }.toSet(),
                    utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                ).modified

        return BehandlingEditedView(modified = modified)
    }

    @PostMapping(value = ["/{behandlingId}/nyankebehandlingka", "/{behandlingId}/nybehandlingfratrygderettbehandling"])
    fun nyAnkebehandlingKA(
        @PathVariable("behandlingId") behandlingId: UUID,
    ) {
        logMethodDetails(
            methodName = ::nyAnkebehandlingKA.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        behandlingService.validateTrygderettenbehandlingBeforeNyBehandling(behandlingId)

        behandlingService.setNyBehandlingKAAndSetToAvsluttet(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @GetMapping("/{behandlingId}/history")
    fun getHistory(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): HistoryResponse {
        logMethodDetails(
            methodName = ::getHistory.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getHistory(behandlingId = behandlingId)
    }

    @Operation(
        summary = "Søk relevante oppgaver som gjelder en gitt person",
        description = "Finner alle relevante oppgaver som omhandler en gitt person.",
    )
    @GetMapping("/{behandlingId}/relevant", produces = ["application/json"])
    fun findRelevantBehandlinger(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): RelevantBehandlingerResponse {
        logMethodDetails(
            methodName = ::findRelevantBehandlinger.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val behandlinger: List<Behandling> = behandlingService.findRelevantBehandlinger(behandlingId = behandlingId)

        return RelevantBehandlingerResponse(
            aapneBehandlinger =
                behandlinger
                    .filter { it.id != behandlingId && it.sattPaaVent == null }
                    .sortedByDescending { it.mottattKlageinstans }
                    .map { it.id },
            paaVentBehandlinger =
                behandlinger
                    .filter { it.id != behandlingId && it.sattPaaVent != null }
                    .sortedByDescending { it.mottattKlageinstans }
                    .map { it.id },
        )
    }

    @Operation(
        summary = "Hent oppgaver i Gosys gjelder personen i behandlingen",
        description = "Finner alle Gosys-oppgaver som gjelder personen behandlingen gjelder.",
    )
    @GetMapping("/{behandlingId}/gosysoppgaver", produces = ["application/json"])
    fun findGosysoppgaver(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<GosysOppgaveView> {
        logMethodDetails(
            methodName = ::findGosysoppgaver.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.findRelevantGosysOppgaver(
            behandlingId = behandlingId,
        )
    }

    @Operation(
        summary = "Hent gjeldende Gosys-oppgave for behandlingen",
        description = "Henter en Gosys-oppgave.",
    )
    @GetMapping("/{behandlingId}/gosysoppgave", produces = ["application/json"])
    fun getGosysOppgave(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): GosysOppgaveView {
        logMethodDetails(
            methodName = ::getGosysOppgave.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return behandlingService.getGosysOppgave(
            behandlingId = behandlingId,
        )
    }

    @GetMapping("/{behandlingId}/fradelingreason")
    fun getFradelingReason(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): WithPrevious<TildelingEvent>? {
        logBehandlingMethodDetails(
            methodName = ::getFradelingReason.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.getFradelingReason(behandlingId)
    }
}
