package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.klage.oppgave.clients.norg2.Enhet
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
class Norg2Controller(
    val norg2Client: Norg2Client
) {

    @GetMapping("/enhet/{enhetNr}")
    fun getEnhet(@PathVariable("enhetNr") enhetNr: String): Enhet = norg2Client.fetchEnhet(enhetNr)

}
