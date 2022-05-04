package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.AnkeBasertPaaKlageInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.klage.MuligAnke
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("muliganke")
class MuligAnkeController(
    private val klagebehandlingService: KlagebehandlingService,
    private val mottakService: MottakService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{fnr}")
    fun getMuligeAnkerByFnr(
        @PathVariable("fnr") fnr: String
    ): List<MuligAnke> {
        return klagebehandlingService.findMuligAnkeByPartId(fnr)
    }

    @GetMapping("/{fnr}/{klagebehandlingId}")
    fun getMuligAnkeByFnrAndKlagebehandlingId(
        @PathVariable("fnr") fnr: String,
        @PathVariable("klagebehandlingId") klagebehandlingId: UUID
    ): MuligAnke? {
        return klagebehandlingService.findMuligAnkeByPartIdAndKlagebehandlingId(fnr, klagebehandlingId)
    }

    @PostMapping("/opprettanke")
    fun sendInnAnkeBasertPaaKabalKlage(
        @RequestBody input: AnkeBasertPaaKlageInput
    ) {
        mottakService.createAnkeMottakBasertPaaKlagebehandlingId(input)
    }
}