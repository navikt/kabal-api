package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.VedtakEditedView
import no.nav.klage.oppgave.api.view.VedtakHjemlerInput
import no.nav.klage.oppgave.api.view.VedtakUtfallInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.VedtakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class BehandlingVedtakController(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val vedtakService: VedtakService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/{id}/resultat/utfall")
    fun setUtfall(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: VedtakUtfallInput
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::setUtfall.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setUtfall(
                behandlingId,
                input.utfall?.let { Utfall.of(it) },
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{id}/resultat/hjemler")
    fun setHjemler(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: VedtakHjemlerInput
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::setHjemler.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setHjemler(
                behandlingId = behandlingId,
                hjemler = input.hjemler?.map { Registreringshjemmel.of(it) }?.toSet() ?: emptySet(),
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }
}
