package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.kodeverk.LandInfo
import no.nav.klage.oppgave.domain.kodeverk.PostInfo
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*


@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class KodeverkController(
    private val kodeverkService: KodeverkService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/postinfo")
    fun getPostInfo(): List<PostInfo> {
        logMethodDetails(
            ::getPostInfo.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )
        return kodeverkService.getPostInfo()
    }

    @GetMapping("/landinfo")
    fun getLandInfo(): List<LandInfo> {
        logMethodDetails(
            ::getLandInfo.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return kodeverkService.getLandkoder()
    }
}