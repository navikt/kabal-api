package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.IdentifikatorInput
import no.nav.klage.oppgave.api.view.kabin.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.*
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
    private val partSearchService: PartSearchService,
    private val kabinApiService: KabinApiService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/isduplicate")
    fun isDuplicate(
        @RequestBody input: IsDuplicateInput
    ): Boolean {
        logMethodDetails(
            methodName = ::isDuplicate.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )
        return mottakService.isDuplicate(
            fagsystem = Fagsystem.of(input.fagsystemId),
            kildeReferanse = input.kildereferanse,
            type = Type.of(input.typeId)
        )
    }

    /**
     * Should not be used anymore. Kabin should use kabal-api directly instead.
     */
    @PostMapping("/searchpart")
    fun searchPart(
        @RequestBody input: IdentifikatorInput
    ): OldKabinPartView {
        logMethodDetails(
            methodName = ::searchPart.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )
        return partSearchService.searchPart(
            identifikator = input.identifikator
        ).toOldKabinPartView()
    }

    @PostMapping("/ankemuligheter")
    fun getAnkemuligheter(
        @RequestBody input: GetCompletedKlagebehandlingerInput
    ): List<Ankemulighet> {
        logMethodDetails(
            methodName = ::getAnkemuligheter.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getCombinedAnkemuligheter(partIdValue = input.idnummer)
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

    @PostMapping("/createanke")
    fun createAnke(
        @RequestBody input: CreateAnkeBasedOnKabinInput
    ): CreatedAnkeResponse {
        logMethodDetails(
            methodName = ::createAnke.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createAnke(
            input = input
        )
    }

    @PostMapping("/createankefromcompleteinput")
    fun createAnkeFromCompleteInput(
        @RequestBody input: CreateAnkeBasedOnCompleteKabinInput
    ): CreatedAnkeResponse {
        logMethodDetails(
            methodName = ::createAnkeFromCompleteInput.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createAnkeFromCompleteKabinInput(
            input = input
        )
    }

    @GetMapping("/anker/{mottakId}/status")
    fun getCreatedAnkebehandlingStatus(
        @PathVariable mottakId: UUID
    ): CreatedAnkebehandlingStatusForKabin {
        logMethodDetails(
            methodName = ::getCreatedAnkebehandlingStatus.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getCreatedAnkebehandlingStatus(
            mottakId = mottakId
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

    @PostMapping("/createklage")
    fun createKlage(
        @RequestBody input: CreateKlageBasedOnKabinInput
    ): CreatedKlageResponse {
        logMethodDetails(
            methodName = ::createKlage.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.createKlage(
            input = input
        )
    }

    @GetMapping("/klager/{mottakId}/status")
    fun getCreatedKlagebehandlingStatus(
        @PathVariable mottakId: UUID
    ): CreatedKlagebehandlingStatusForKabin {
        logMethodDetails(
            methodName = ::getCreatedKlagebehandlingStatus.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return kabinApiService.getCreatedKlagebehandlingStatus(
            mottakId = mottakId
        )
    }
}

