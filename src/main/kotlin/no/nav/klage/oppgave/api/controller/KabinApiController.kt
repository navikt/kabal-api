package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KabinApiService
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/api/internal")
class KabinApiController(
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val mottakService: MottakService,
    private val kabinApiService: KabinApiService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping(value = ["/checkbehandlingisduplicate"])
    fun checkBehandlingIsDuplicate(
        @RequestBody input: BehandlingIsDuplicateInput
    ): BehandlingIsDuplicateResponse {
        logMethodDetails(
            methodName = ::checkBehandlingIsDuplicate.name,
            innloggetIdent = "N/A",
            logger = logger
        )
        return mottakService.behandlingIsDuplicate(
            fagsystem = Fagsystem.of(input.fagsystemId),
            kildeReferanse = input.kildereferanse,
            type = Type.of(input.typeId)
        )
    }

    @PostMapping(value = ["/isduplicate", "/behandlingisduplicate"])
    fun behandlingIsDuplicate(
        @RequestBody input: BehandlingIsDuplicateInput
    ): Boolean {
        logMethodDetails(
            methodName = ::behandlingIsDuplicate.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )
        return mottakService.behandlingIsDuplicate(
            fagsystem = Fagsystem.of(input.fagsystemId),
            kildeReferanse = input.kildereferanse,
            type = Type.of(input.typeId)
        ).duplicate
    }

    @PostMapping("/oppgaveisduplicate")
    fun gosysOppgaveIsDuplicate(
        @RequestBody input: GosysOppgaveIsDuplicateInput
    ): Boolean {
        logMethodDetails(
            methodName = ::gosysOppgaveIsDuplicate.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )
        return behandlingService.gosysOppgaveIsDuplicate(
            gosysOppgaveId = input.gosysOppgaveId,
        )
    }

    @PostMapping("/ankemuligheter")
    fun getAnkemuligheter(
        @RequestBody input: GetCompletedBehandlingerInput
    ): List<Mulighet> {
        logMethodDetails(
            methodName = ::getAnkemuligheter.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getAnkemuligheter(partIdValue = input.idnummer)
    }

    @PostMapping("/omgjoeringskravmuligheter")
    fun getOmgjoeringskravmuligheter(
        @RequestBody input: GetCompletedBehandlingerInput
    ): List<Mulighet> {
        logMethodDetails(
            methodName = ::getOmgjoeringskravmuligheter.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getOmgjoeringskravmuligheter(partIdValue = input.idnummer)
    }

    @PostMapping("/gjenopptaksmuligheter")
    fun getGjenopptaksmuligheter(
        @RequestBody input: GetCompletedBehandlingerInput
    ): List<Mulighet> {
        logMethodDetails(
            methodName = ::getGjenopptaksmuligheter.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getGjenopptaksmuligheter(partIdValue = input.idnummer)
    }

    @GetMapping("/completedbehandlinger/{behandlingId}")
    fun getCompletedBehandling(
        @PathVariable behandlingId: UUID
    ): CompletedBehandling {
        logMethodDetails(
            methodName = ::getCompletedBehandling.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return behandlingService.findCompletedBehandlingById(
            behandlingId = behandlingId
        )
    }

    @PostMapping("/createbehandling")
    fun createBehandlingFromPreviousKabalBehandling(
        @RequestBody input: CreateBehandlingBasedOnKabinInputWithPreviousKabalBehandling
    ): CreatedBehandlingResponse {
        logMethodDetails(
            methodName = ::createBehandlingFromPreviousKabalBehandling.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createBehandlingFromPreviousKabalBehandling(
            input = input
        )
    }

    @PostMapping("/createklage")
    fun createKlage(
        @RequestBody input: CreateKlageBasedOnKabinInput
    ): CreatedBehandlingResponse {
        logMethodDetails(
            methodName = ::createKlage.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createKlage(
            input = input
        )
    }

    @PostMapping("/createankefromcompleteinput")
    fun createAnkeFromCompleteInput(
        @RequestBody input: CreateAnkeBasedOnCompleteKabinInput
    ): CreatedBehandlingResponse {
        logMethodDetails(
            methodName = ::createAnkeFromCompleteInput.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createAnkeFromCompleteKabinInput(
            input = input
        )
    }

    @PostMapping("/create-behandling-based-on-journalpost")
    fun createBehandlingBasedOnJournalpost(
        @RequestBody input: CreateBehandlingBasedOnJournalpostInput
    ): CreatedBehandlingResponse {
        logMethodDetails(
            methodName = ::createBehandlingBasedOnJournalpost.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createBehandlingBasedOnJournalpost(
            input = input
        )
    }

    @PostMapping("/searchusedjournalpostid")
    fun getUsedJournalpostIdListForPerson(
        @RequestBody input: SearchUsedJournalpostIdInput,
    ): List<String> {
        logMethodDetails(
            methodName = ::getUsedJournalpostIdListForPerson.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return mottakService.getUsedJournalpostIdList(
            sakenGjelder = input.fnr
        )
    }

    @GetMapping("/behandlinger/{behandlingId}/status")
    fun getCreatedBehandlingStatus(
        @PathVariable behandlingId: UUID
    ): CreatedBehandlingStatusForKabin {
        logMethodDetails(
            methodName = ::CreatedBehandlingStatusForKabin.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getCreatedBehandlingStatus(
            behandlingId = behandlingId
        )
    }
}

