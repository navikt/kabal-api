package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger")
class BehandlingController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{behandlingId}/sattpaavent")
    fun setSattPaaVent(
        @Parameter(description = "Id til en behandling")
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: SattPaaVentInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setSattPaaVent.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setSattPaaVent(
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
            ::deleteSattPaaVent.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.setSattPaaVent(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            input = null
        )
        return BehandlingEditedView(modified = modified)
    }

    @PostMapping("/{behandlingId}/fullfoer")
    fun fullfoerBehandling(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestParam(value = "nybehandling", required = false) nyBehandling: Boolean = false
    ): BehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            ::fullfoerBehandling.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val klagebehandling = behandlingService.ferdigstillBehandling(
            behandlingId = behandlingId,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            nyBehandling = nyBehandling,
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
        @RequestBody input: BehandlingDateNullableInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setKjennelseMottatt.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setKjennelseMottatt(
            behandlingId = behandlingId,
            date = input.date?.atStartOfDay(),
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
    @GetMapping("/{behandlingId}/validate", "/{behandlingId}/validate/fullfoer")
    fun validate(
        @PathVariable("behandlingId") behandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validate.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        behandlingService.validateBehandlingBeforeFinalize(behandlingId, false)
        return ValidationPassedResponse()
    }

    /**
     * Valgfri validering før feilregistrering.
     */
    @GetMapping("/{behandlingId}/validate/feilregistrer")
    fun validateFeilregistrering(
        @PathVariable("behandlingId") behandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validateFeilregistrering.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        behandlingService.validateFeilregistrering(behandlingId)
        return ValidationPassedResponse()
    }

    /**
     * Valgfri validering før ny ankebehandling.
     */
    @GetMapping("/{behandlingId}/validate/nyankebehandling")
    fun validateAnkebehandling(
        @PathVariable("behandlingId") behandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validateAnkebehandling.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        behandlingService.validateAnkeITrygderettenbehandlingBeforeNyAnkebehandling(behandlingId)
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
            hjemler = input.hjemmelIdList ?: input.hjemler ?: emptyList(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return BehandlingEditedView(modified = modified)
    }

    @PutMapping("/{behandlingId}/fullmektig")
    fun setFullmektig(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: NullableIdentifikatorInput,
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

    @PutMapping("/{behandlingId}/klager")
    fun setKlager(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: IdentifikatorInput,
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setKlager.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setKlager(
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

    @GetMapping("/{behandlingId}/potentialrol")
    fun getPotentialROL(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Rols {
        logMethodDetails(
            ::getPotentialROL.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingService.getPotentialROLForBehandling(behandlingId = behandlingId)
    }

    @GetMapping("/{behandlingId}/sakengjelder")
    fun getSakenGjelder(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): BehandlingDetaljerView.SakenGjelderView {
        logMethodDetails(
            ::getSakenGjelder.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingMapper.getSakenGjelderView(
            behandlingService.getBehandling(behandlingId).sakenGjelder
        )
    }

    @GetMapping("/{behandlingId}/ainntekt")
    fun getAInntektUrl(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ModelAndView {
        logMethodDetails(
            ::getAInntektUrl.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return ModelAndView(/* viewName = */ "redirect:" + behandlingService.getAInntektUrl(behandlingId))
    }

    @GetMapping("/{behandlingId}/aaregister")
    fun getAARegisterUrl(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): ModelAndView {
        logMethodDetails(
            ::getAARegisterUrl.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return ModelAndView(/* viewName = */ "redirect:" + behandlingService.getAARegisterUrl(behandlingId))
    }

    @PostMapping("/{behandlingId}/feilregistrer")
    fun setBehandlingFeilregistrert(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: FeilregistreringInput,
    ): FeilregistreringResponse {
        logMethodDetails(
            ::setBehandlingFeilregistrert.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return behandlingService.feilregistrer(
            behandlingId = behandlingId,
            reason = input.reason,
            fagsystem = Fagsystem.KABAL,
        )
    }

    @PutMapping("/{behandlingId}/resultat/utfall")
    fun setUtfall(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakUtfallInput
    ): UtfallEditedView {
        logBehandlingMethodDetails(
            ::setUtfall.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val utfallId = input.utfallId ?: input.utfall

        val behandling = behandlingService.setUtfall(
            behandlingId = behandlingId,
            utfall = if (utfallId != null) Utfall.of(utfallId) else null,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return UtfallEditedView(
            modified = behandling.modified,
            utfallId = behandling.utfall?.id,
            extraUtfallIdSet = behandling.extraUtfallSet.map { it.id }.toSet(),
        )
    }

    @PutMapping("/{behandlingId}/resultat/extra-utfall-set")
    fun setUtfallSet(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakExtraUtfallSetInput
    ): ExtraUtfallEditedView {
        logBehandlingMethodDetails(
            ::setUtfallSet.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val behandling = behandlingService.setExtraUtfallSet(
            behandlingId = behandlingId,
            extraUtfallSet = input.extraUtfallIdSet.map { Utfall.of(it) }.toSet(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        return ExtraUtfallEditedView(
            modified = behandling.modified,
            extraUtfallIdSet = behandling.extraUtfallSet.map { it.id }.toSet(),
        )
    }

    @PutMapping("/{behandlingId}/resultat/hjemler")
    fun setRegistreringshjemler(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: VedtakHjemlerInput
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setRegistreringshjemler.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val modified = behandlingService.setRegistreringshjemler(
            behandlingId = behandlingId,
            registreringshjemler = input.hjemmelIdSet?.map { Registreringshjemmel.of(it) }?.toSet()
                ?: input.hjemler?.map { Registreringshjemmel.of(it) }?.toSet() ?: emptySet(),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        ).modified

        return BehandlingEditedView(modified = modified)
    }

    @PostMapping("/{behandlingId}/nyankebehandlingka")
    fun nyAnkebehandlingKA(
        @PathVariable("behandlingId") behandlingId: UUID,
    ) {
        logMethodDetails(
            ::nyAnkebehandlingKA.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        behandlingService.validateAnkeITrygderettenbehandlingBeforeNyAnkebehandling(behandlingId)

        behandlingService.setNyAnkebehandlingKA(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
    }
}