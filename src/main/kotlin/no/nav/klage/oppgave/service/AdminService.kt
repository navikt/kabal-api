package no.nav.klage.oppgave.service

import jakarta.persistence.EntityManager
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsMellomlagret
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.InnholdsfortegnelseService
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToRegistreringshjemlerV2
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.DOK_DIST_KANAL
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ENHETER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ENHET_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_ENHETSMAPPER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_ENHETSMAPPE_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_GJELDER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_OPPGAVETYPE_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.KRR_INFO_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.LANDKODER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.PERSON_ADDRESS
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.POSTSTEDER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.SAKSBEHANDLER_NAME_CACHE
import no.nav.klage.oppgave.config.SchedulerHealthGate
import no.nav.klage.oppgave.domain.PersonProtection
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.service.StatistikkTilDVHService.Companion.TR_ENHET
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import no.nav.slackposter.SlackClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class AdminService(
    private val kafkaDispatcher: KafkaDispatcher,
    private val behandlingRepository: BehandlingRepository,
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val ankeITrygderettenbehandlingRepository: AnkeITrygderettenbehandlingRepository,
    private val omgjoeringskravbehandlingRepository: OmgjoeringskravbehandlingRepository,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
    private val kafkaEventRepository: KafkaEventRepository,
    private val fileApiClient: FileApiClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingService: BehandlingService,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val tokenUtil: TokenUtil,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val personService: PersonService,
    private val minsideMicrofrontendService: MinsideMicrofrontendService,
    private val slackClient: SlackClient,
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val entityManager: EntityManager,
    private val schedulerHealthGate: SchedulerHealthGate,
    private val merkantilRepository: TaskListMerkantilRepository,
    private val klagebehandlingService: KlagebehandlingService,
    private val sakPersongalleriRepository: SakPersongalleriRepository,
    private val klageLookupGateway: KlageLookupGateway,
    private val personProtectionRepository: PersonProtectionRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Value($$"${KLAGE_BACKEND_GROUP_ID}")
    lateinit var klageBackendGroupId: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    /**
     * Intentionally NOT @Transactional at method level: this can run for a long time and a
     * single transaction would hold one DB connection for the entire duration, eventually
     * tripping Hikari maxLifetime / network idle timeouts ("This connection has been closed.").
     *
     * We also avoid holding a DB transaction across Kafka I/O. Each Kafka send blocks on
     * .get() for a network round-trip; if many of those are wrapped in a single tx the DB
     * connection sits "idle in transaction" and PostgreSQL's idle_in_transaction_session_timeout
     * will close it, producing "Session/EntityManager is closed" on the next repo call.
     *
     * Strategy:
     *   1. Fetch a page of IDs (ordered by created desc) in its own short tx.
     *   2. For each ID, open a short tx that loads the behandling and sends it to Kafka.
     *   3. Repeat until no more pages.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun syncKafkaWithDb() {
        var pageNumber = 0
        val pageSize = 50
        var hasNext: Boolean
        do {
            val pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by("created").descending())

            val pageResult = transactionTemplate.execute {
                val page = behandlingRepository.findAll(pageRequest)
                page.content.map { it.id } to page.hasNext()
            } ?: (emptyList<UUID>() to false)

            val ids = pageResult.first
            hasNext = pageResult.second

            ids.forEach { id ->
                try {
                    transactionTemplate.execute {
                        val behandling = behandlingRepository.findByIdEager(id)
                        behandlingEndretKafkaProducer.sendBehandlingEndret(behandling)
                    }
                } catch (e: Exception) {
                    logger.warn("Exception while syncing behandling $id to Kafka", e)
                }
            }

            pageNumber++
        } while (hasNext)
    }

    @Transactional
    fun reindexBehandlingInSearch(behandlingId: UUID) {
        behandlingEndretKafkaProducer.sendBehandlingEndret(
            behandlingRepository.findByIdEager(behandlingId)
        )
    }

    /** only for use in dev */
    @Transactional
    fun deleteBehandlingInDev(behandlingId: UUID) {
        logger.debug("Delete test data in dev: attempt to delete behandling with id {}", behandlingId)
        val (hoveddokumenter, vedlegg) = dokumentUnderArbeidRepository.findByBehandlingId(behandlingId)
            .partition { it is DokumentUnderArbeidAsHoveddokument }

        for (dua in hoveddokumenter + vedlegg) {
            try {
                if (dua is DokumentUnderArbeidAsMellomlagret && dua.mellomlagerId != null) {
                    fileApiClient.deleteDocument(id = dua.mellomlagerId!!, systemUser = true)
                }
            } catch (e: Exception) {
                logger.warn("Delete test data in dev: Could not delete from file api")
            }

            try {
                if (dua is DokumentUnderArbeidAsHoveddokument) {
                    innholdsfortegnelseService.deleteInnholdsfortegnelse(dua.id)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't delete innholdsfortegnelse. May be b/c there never was one.")
            }

            //TODO Slette smartdocs
        }

        dokumentUnderArbeidRepository.deleteAll(vedlegg)

        dokumentUnderArbeidRepository.deleteAll(hoveddokumenter)

        merkantilRepository.findByBehandlingId(behandlingId = behandlingId).ifPresent {
            merkantilRepository.delete(it)
        }

        val behandling = behandlingRepository.findById(behandlingId).get()

        behandlingRepository.deleteById(behandlingId)

        //Delete in search
        behandlingEndretKafkaProducer.sendBehandlingDeleted(behandlingId)

        if (behandling.shouldUpdateInfotrygd()) {
            logger.debug("Feilregistrering av behandling skal registreres i Infotrygd.")
            klageFssProxyClient.setToFeilregistrertInKabal(
                sakId = behandling.kildeReferanse,
                input = FeilregistrertInKabalInput(
                    saksbehandlerIdent = systembrukerIdent,
                )
            )
            logger.debug("Feilregistrering av behandling ble registrert i Infotrygd.")
        }

        //Delete in dokumentarkiv? Probably not necessary. They clean up when they need to.
    }

    @Transactional
    fun resendToDVH() {
        logger.debug("Attempting to resend all events to DVH")
        kafkaDispatcher.dispatchEventsToKafka(
            EventType.STATS_DVH,
            listOf(UtsendingStatus.IKKE_SENDT, UtsendingStatus.FEILET, UtsendingStatus.SENDT)
        )
    }

    @Transactional
    fun migrateDvhEvents() {
        val events = kafkaEventRepository.findByType(EventType.STATS_DVH)

        val filteredEvents = events.filter {
            val parsedStatistikkTilDVH = jacksonObjectMapper.readValue(it.jsonPayload, StatistikkTilDVH::class.java)
            parsedStatistikkTilDVH.behandlingType == "Anke" &&
                    parsedStatistikkTilDVH.behandlingStatus in listOf(
                BehandlingState.AVSLUTTET,
                BehandlingState.AVSLUTTET_I_TR_OG_NY_ANKEBEHANDLING_I_KA
            )
        }

        logger.debug("Number of candidates: ${filteredEvents.size}")

        filteredEvents.forEach {
            if (ankeITrygderettenbehandlingRepository.existsById(it.behandlingId)) {
                logger.debug(
                    "BEFORE: Modifying kafka event {}, behandling_id {}, payload: {}",
                    it.id,
                    it.behandlingId,
                    it.jsonPayload
                )
                var parsedStatistikkTilDVH = jacksonObjectMapper.readValue(it.jsonPayload, StatistikkTilDVH::class.java)
                parsedStatistikkTilDVH = parsedStatistikkTilDVH.copy(
                    ansvarligEnhetKode = TR_ENHET,
                    tekniskTid = LocalDateTime.now()
                )
                it.jsonPayload = jacksonObjectMapper.writeValueAsString(parsedStatistikkTilDVH)
                it.status = UtsendingStatus.IKKE_SENDT
                logger.debug(
                    "AFTER: Modified kafka event {}, behandling_id {}, payload: {}",
                    it.id,
                    it.behandlingId,
                    it.jsonPayload
                )
            }
        }
    }

    @Transactional
    fun logExpiredUsers() {
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        val saksbehandlerSet = unfinishedBehandlinger.mapNotNull { it.tildeling?.saksbehandlerident }.toSet()
        val saksbehandlerSluttdatoInfo =
            klageLookupGateway.getSluttdatoForNavIdentList(navIdentList = saksbehandlerSet.toList())
        var saksbehandlerLogOutput = ""
        saksbehandlerSluttdatoInfo.forEach {
            if (it.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                saksbehandlerLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        logger.debug("Expired, assigned saksbehandler: \n $saksbehandlerLogOutput")

        val medunderskriverSet = unfinishedBehandlinger.mapNotNull { it.medunderskriver?.saksbehandlerident }.toSet()
        val medunderskriverSluttdatoInfo =
            klageLookupGateway.getSluttdatoForNavIdentList(navIdentList = medunderskriverSet.toList())
        var medunderskriverLogOutput = ""
        medunderskriverSluttdatoInfo.forEach {
            if (it.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                medunderskriverLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        logger.debug("Expired, assigned medunderskriver: \n $medunderskriverLogOutput")

        val rolSet = unfinishedBehandlinger.mapNotNull { it.rolIdent }.toSet()
        val rolSluttdatoInfo = klageLookupGateway.getSluttdatoForNavIdentList(navIdentList = rolSet.toList())
        var rolLogOutput = ""
        rolSluttdatoInfo.forEach {
            if (it.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                rolLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        logger.debug("Expired, assigned rol: \n $rolLogOutput")
    }

    @Transactional
    fun logProtected() {
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        teamLogger.debug("Checking for protected users")
        unfinishedBehandlinger.forEach { behandling ->
            if (behandling.sakenGjelder.partId.type == PartIdType.PERSON) {
                try {
                    val person = personService.getPerson(fnr = behandling.sakenGjelder.partId.value)
                    if (person.strengtFortrolig || person.strengtFortroligUtland) {
                        teamLogger.debug("Protected user in behandling with id {}", behandling.id)
                    }
                } catch (e: Exception) {
                    teamLogger.debug("Couldn't check person", e)
                }
            }
        }
    }

    @Transactional
    @Scheduled(cron = $$"${FIND_INACCESSIBLE_BEHANDLINGER_CRON}", zone = "Europe/Oslo")
    @SchedulerLock(name = "findInaccessibleBehandlinger", lockAtLeastFor = "PT1M")
    fun logInaccessibleBehandlinger() {
        if (!schedulerHealthGate.isReady()) return
        val unfinishedBehandlinger =
            behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullWithHjemler()
        teamLogger.debug(
            "Checking for inaccessible behandlinger. Number of unfinished behandlinger: {}",
            unfinishedBehandlinger.size
        )

        val resultMessage =
            checkForUnavailableDueToBeskyttelseAndSkjerming(unfinishedBehandlingerInput = unfinishedBehandlinger) +
                    checkForUnavailableDueToHjemler(unfinishedBehandlingerInput = unfinishedBehandlinger)
        slackClient.postMessage("<!subteam^$klageBackendGroupId>: \n$resultMessage")
        teamLogger.debug(resultMessage)
    }

    fun checkForUnavailableDueToBeskyttelseAndSkjerming(unfinishedBehandlingerInput: List<Behandling>?): String {
        val unfinishedBehandlinger =
            unfinishedBehandlingerInput ?: behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        val start = System.currentTimeMillis()

        val strengtFortroligBehandlinger = mutableSetOf<String>()
        val fortroligBehandlinger = mutableSetOf<String>()
        val egenAnsattBehandlinger = mutableSetOf<String>()

        unfinishedBehandlinger.forEach { behandling ->
            if (behandling.sakenGjelder.partId.type == PartIdType.PERSON) {
                try {
                    val person = personService.getPerson(fnr = behandling.sakenGjelder.partId.value)
                    if (person.strengtFortrolig || person.strengtFortroligUtland) {
                        strengtFortroligBehandlinger.add(behandling.id.toString())
                    }

                    if (person.fortrolig) {
                        behandling.tildeling?.saksbehandlerident?.let {
                            if (!saksbehandlerService.hasFortroligRole(ident = it)) {
                                fortroligBehandlinger.add(behandling.id.toString())
                            }
                        }
                    }

                    if (person.egenAnsatt) {
                        behandling.tildeling?.saksbehandlerident?.let {
                            if (!saksbehandlerService.hasEgenAnsattRole(ident = it)) {
                                egenAnsattBehandlinger.add(behandling.id.toString())
                            }
                        }
                    }
                } catch (e: Exception) {
                    teamLogger.debug("Couldn't check person", e)
                }
            }
        }

        val resultMessage =
            "Fullført søk etter utilgjengelige behandlinger. \n" +
                    "Strengt fortrolige behandlinger: $strengtFortroligBehandlinger \n" +
                    "Fortrolige behandlinger der saksbehandler mangler tilgang: $fortroligBehandlinger \n" +
                    "Egen ansatt-behandlinger der saksbehandler mangler tilgang: $egenAnsattBehandlinger\n\n"

        val end = System.currentTimeMillis()
        teamLogger.debug("Time it took to process unavailableDueToBeskyttelseAndSkjerming: ${end - start} millis")

        return resultMessage
    }

    fun checkForUnavailableDueToHjemler(unfinishedBehandlingerInput: List<Behandling>?): String {
        val unfinishedBehandlinger =
            unfinishedBehandlingerInput
                ?: behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullWithHjemler()
        val start = System.currentTimeMillis()
        val unavailableBehandlinger = mutableSetOf<Behandling>()
        val missingHjemmelInRegistryBehandling = mutableSetOf<Pair<UUID, Set<Hjemmel>>>()
        val ytelseToHjemlerMap = mutableMapOf<Ytelse, Set<Hjemmel>>()

        unfinishedBehandlinger.forEach { behandling ->
            when (behandling) {
                is Klagebehandling, is Ankebehandling, is Omgjoeringskravbehandling -> {
                    if (behandling.tildeling == null) {
                        val hjemlerForYtelseInInnstillinger = ytelseToHjemlerMap.getOrPut(behandling.ytelse) {
                            kabalInnstillingerService.getRegisteredHjemlerForYtelse(behandling.ytelse)
                        }
                        if (behandling.hjemler.all {
                                it !in hjemlerForYtelseInInnstillinger
                            }
                        ) {
                            unavailableBehandlinger.add(behandling)
                        } else if (behandling.hjemler.any { it !in hjemlerForYtelseInInnstillinger }) {
                            missingHjemmelInRegistryBehandling.add(
                                Pair(
                                    behandling.id,
                                    behandling.hjemler.filter { it !in hjemlerForYtelseInInnstillinger }.toSet()
                                )
                            )
                        }
                    }
                }

                else -> null
            }
        }

        var errorLog = "Utilgjengelige behandlinger på grunn av hjemler: \n"
        unavailableBehandlinger.forEach { foundBehandling ->
            errorLog += "Behandling-id: ${foundBehandling.id}, Hjemler: ${foundBehandling.hjemler} \n"
        }
        errorLog += "Behandlinger med hjemler som ikke fins i innstillinger: \n"
        missingHjemmelInRegistryBehandling.forEach {
            errorLog += "Behandling-id: ${it.first}, Hjemler: ${it.second} \n"
        }

        val end = System.currentTimeMillis()
        teamLogger.debug("Time it took to process unavailableDueToHjemler: ${end - start} millis")

        return errorLog
    }

    @Transactional
    @Scheduled(cron = "\${SETTINGS_CLEANUP_CRON}", zone = "Europe/Oslo")
    @SchedulerLock(name = "cleanupExpiredAssignees")
    fun cleanupExpiredAssignees() {
        if (!schedulerHealthGate.isReady()) return
        logger.debug("Running scheduled expired assignee check.")
        handleInvalidUsers()
        logger.debug("Scheduled expired assignee check completed.")
    }

    fun handleInvalidUsers() {
        val unfinishedBehandlingerWithRol =
            behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullAndRolIdentIsNotNull()
                .filter { it.rolFlowState != FlowState.RETURNED }

        val unfinishedBehandlingerWithMu =
            behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullAndMedunderskriverIsNotNull()
                .filter { it.medunderskriverFlowState != FlowState.RETURNED }

        val unfinishedBehandlingerWithTildeling =
            behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullAndTildelingIsNotNull()

        val rolCandidates = unfinishedBehandlingerWithRol
            .asSequence()
            .mapNotNull { it.rolIdent }
            .toSet()

        val muCandidates = unfinishedBehandlingerWithMu
            .asSequence()
            .mapNotNull { it.medunderskriver!!.saksbehandlerident }
            .toSet()

        val tildelingCandidates = unfinishedBehandlingerWithTildeling
            .asSequence()
            .mapNotNull { it.tildeling!!.saksbehandlerident }
            .toSet()

        val identsToRemove = getUsersToRemove(rolCandidates + muCandidates + tildelingCandidates)

        logger.debug("Found expired idents: $identsToRemove, proceeding to remove from behandlinger.")

        val behandlingerWhereRolShouldBeRemoved =
            unfinishedBehandlingerWithRol.filter { it.rolIdent in identsToRemove }

        val behandlingerWhereMuShouldBeRemoved =
            unfinishedBehandlingerWithMu.filter { it.medunderskriver!!.saksbehandlerident in identsToRemove }

        val behandlingerWhereTildelingShouldBeRemoved =
            unfinishedBehandlingerWithTildeling.filter { it.tildeling!!.saksbehandlerident in identsToRemove }

        logger.debug("Found removables: $behandlingerWhereRolShouldBeRemoved, $behandlingerWhereMuShouldBeRemoved, $behandlingerWhereTildelingShouldBeRemoved")

        behandlingerWhereRolShouldBeRemoved
            .asSequence()
            .forEach {
                logger.debug("Behandling ${it.id} has expired rol: ${it.rolIdent}, setting to null.")
                behandlingService.setRolToNullInSystemContext(it.id)
            }

        behandlingerWhereMuShouldBeRemoved
            .asSequence()
            .forEach {
                logger.debug("Behandling ${it.id} has expired mu: ${it.medunderskriver!!.saksbehandlerident}, setting to null.")
                behandlingService.setMedunderskriverAndMedunderskriverFlowToNull(
                    behandlingId = it.id,
                    systemUserContext = true
                )
            }

        behandlingerWhereTildelingShouldBeRemoved
            .asSequence()
            .forEach {
                logger.debug("Behandling ${it.id} has expired tildelt saksbehandler: ${it.tildeling!!.saksbehandlerident}, setting to null.")
                behandlingService.setExpiredTildeltSaksbehandlerToNullInSystemContext(it.id)
            }
    }

    private fun getUsersToRemove(candidates: Set<String>): Set<String> {
        if (candidates.isEmpty()) return emptySet()

        val sluttdatoList = klageLookupGateway.getSluttdatoForNavIdentList(navIdentList = candidates.toList())

        val usersNoLongerInNav = sluttdatoList.filter {
            it.sluttdato?.isBefore(
                LocalDate.now().minusWeeks(1)
            ) == true
        }
            .map { it.navIdent }
            .toSet()

        logger.debug("Found users no longer in Nav: $usersNoLongerInNav")
        val furtherCandidates = candidates - usersNoLongerInNav

        val enhetByNavn = Enhet.entries.associateBy { it.navn }
        val allowedEnheter = (styringsenheter + klageenheter).toSet()

        val usersNoLongerInCorrectEnhet =
            if (furtherCandidates.isEmpty()) {
                emptySet()
            } else {
                klageLookupGateway.getUserInfoForNavIdentList(navIdentList = furtherCandidates.toList())
                    .asSequence()
                    .filter { info ->
                        val enhet = enhetByNavn[info.enhet.enhetId]
                        enhet !in allowedEnheter
                    }
                    .map { it.navIdent }
                    .toSet()
            }

        logger.debug("Found users no longer in enhet: $usersNoLongerInCorrectEnhet")

        val usersToRemove = usersNoLongerInNav + usersNoLongerInCorrectEnhet
        return usersToRemove
    }

    @Transactional
    fun logInvalidRegistreringshjemler() {
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNull()
        val ytelseAndHjemmelPairSet = unfinishedBehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, invalidHjemler) = ytelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseToRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredInvalidHjemler = invalidHjemler.filter { it.second.isNotEmpty() }
        teamLogger.debug("Invalid registreringshjemler in unfinished behandlinger: {}", filteredInvalidHjemler)

        val klagebehandlinger = klagebehandlingRepository.findByKakaKvalitetsvurderingVersionIs(2)
        val klageYtelseAndHjemmelPairSet = klagebehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, klageinvalidHjemler) = klageYtelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseToRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredKlageInvalidHjemler = klageinvalidHjemler.filter { it.second.isNotEmpty() }
        teamLogger.debug("Invalid registreringshjemler in klagebehandlinger v2: {}", filteredKlageInvalidHjemler)

        val ankebehandlinger = ankebehandlingRepository.findByKakaKvalitetsvurderingVersionIs(2)
        val ankeYtelseAndHjemmelPairSet = ankebehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, ankeinvalidHjemler) = ankeYtelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseToRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredAnkeInvalidHjemler = ankeinvalidHjemler.filter { it.second.isNotEmpty() }
        teamLogger.debug("Invalid registreringshjemler in ankebehandlinger v2: {}", filteredAnkeInvalidHjemler)
    }

    @Transactional
    fun migrateTilbakekreving() {
        val candidates = behandlingRepository.findByTilbakekrevingIsFalse()
        logger.debug("Found ${candidates.size} candidates for tilbakekreving migration.")
        var migrations = 0
        candidates.forEach { candidate ->
            if (candidate.registreringshjemler.any {
                    it in tilbakekrevingHjemler
                }
            ) {
                candidate.tilbakekreving = true
                migrations++
            }
        }
        logger.debug("Migrated $migrations candidates.")
    }

    @Transactional
    fun generateOpprettetEvents(behandlingId: UUID? = null) {
        if (behandlingId != null) {
            logger.debug("Generating opprettetEvent for behandlingId: $behandlingId")
            val behandling = behandlingRepository.findByIdEager(behandlingId)
            applicationEventPublisher.publishEvent(
                BehandlingChangedEvent(
                    behandling = behandling,
                    changeList = listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = when (behandling) {
                                is Klagebehandling -> BehandlingChangedEvent.Felt.KLAGEBEHANDLING_OPPRETTET
                                is Ankebehandling -> BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET
                                is Omgjoeringskravbehandling -> BehandlingChangedEvent.Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET
                                else -> throw IllegalArgumentException("Unknown behandling type: ${behandling.type}")
                            },
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = behandling.id,
                        )
                    )
                )
            )
            behandling.opprettetSendt = true
            logger.debug("Generated opprettetEvent for behandlingId: $behandlingId")
        } else {
            val klagebehandlinger = klagebehandlingRepository.findAll()
            logger.debug("Generating opprettetEvents for ${klagebehandlinger.size} klagebehandlinger")
            var klagebehandlingIds = ""
            klagebehandlinger.forEach { behandling ->
                if (!behandling.opprettetSendt) {
                    applicationEventPublisher.publishEvent(
                        /* event = */ BehandlingChangedEvent(
                            behandling = behandling,
                            changeList = listOfNotNull(
                                createChange(
                                    saksbehandlerident = systembrukerIdent,
                                    felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_OPPRETTET,
                                    fraVerdi = null,
                                    tilVerdi = "Opprettet",
                                    behandlingId = behandling.id,
                                )
                            )
                        )
                    )
                    behandling.opprettetSendt = true
                    klagebehandlingIds += "${behandling.id}, "
                }
            }
            val ankebehandlinger = ankebehandlingRepository.findAll()
            logger.debug("Generating opprettetEvents for ${ankebehandlinger.size} ankebehandlinger")
            var ankebehandlingIds = ""
            ankebehandlinger.forEach { behandling ->
                if (!behandling.opprettetSendt) {
                    applicationEventPublisher.publishEvent(
                        BehandlingChangedEvent(
                            behandling = behandling,
                            changeList = listOfNotNull(
                                createChange(
                                    saksbehandlerident = systembrukerIdent,
                                    felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_OPPRETTET,
                                    fraVerdi = null,
                                    tilVerdi = "Opprettet",
                                    behandlingId = behandling.id,
                                )
                            )
                        )
                    )
                    behandling.opprettetSendt = true
                    ankebehandlingIds += "${behandling.id}, "
                }
            }
            val omgjoeringskravbehandlinger = omgjoeringskravbehandlingRepository.findAll()
            logger.debug("Generating opprettetEvents for ${omgjoeringskravbehandlinger.size} omgjoeringskravbehandlinger")
            var omgjoeringskravbehandlingIds = ""
            omgjoeringskravbehandlinger.forEach { behandling ->
                if (!behandling.opprettetSendt) {
                    applicationEventPublisher.publishEvent(
                        BehandlingChangedEvent(
                            behandling = behandling,
                            changeList = listOfNotNull(
                                createChange(
                                    saksbehandlerident = systembrukerIdent,
                                    felt = BehandlingChangedEvent.Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET,
                                    fraVerdi = null,
                                    tilVerdi = "Opprettet",
                                    behandlingId = behandling.id,
                                )
                            )
                        )
                    )
                    behandling.opprettetSendt = true
                    omgjoeringskravbehandlingIds += "${behandling.id}, "
                }
            }
            logger.debug(
                "Successfully generated opprettetEvents for behandlinger: \nKlagebehandlinger: {} \nAnkebehandlinger: {} \nOmgjoeringskravbehandlinger: {}",
                klagebehandlingIds,
                ankebehandlingIds,
                omgjoeringskravbehandlingIds
            )
        }
    }

    @Transactional
    fun getInfotrygdsak(sakId: String): SakFromKlanke {
        return klageFssProxyClient.getSakWithAppAccess(
            sakId = sakId,
            input = GetSakAppAccessInput(
                saksbehandlerIdent = tokenUtil.getIdent(),
            )
        )
    }

    val tilbakekrevingHjemler = listOf(
        Registreringshjemmel.FTRL_22_15_TILBAKEKREVING,
        Registreringshjemmel.FTRL_22_15A,
        Registreringshjemmel.FTRL_22_15B,
        Registreringshjemmel.FTRL_22_15C,
        Registreringshjemmel.FTRL_22_15G,
        Registreringshjemmel.FTRL_22_15D,
        Registreringshjemmel.FTRL_22_15E,
        Registreringshjemmel.FTRL_22_15F,
        Registreringshjemmel.FORSKL_8,
        Registreringshjemmel.INNKL_25_T,
        Registreringshjemmel.INNKL_26A_T,
        Registreringshjemmel.INNKL_26B_T,
        Registreringshjemmel.INNKL_29,
        Registreringshjemmel.FTRL_22_17A,
        Registreringshjemmel.FTRL_4_28,
        Registreringshjemmel.SUP_ST_L_13,
        Registreringshjemmel.BTRL_13,
        Registreringshjemmel.KONTSL_11,
    )

    @Transactional
    fun enableMinsideMicrofrontend(behandlingId: UUID) {
        val behandling = behandlingRepository.findByIdEager(behandlingId)
        if (behandling.feilregistrering != null) {
            logger.debug("Behandling has feilregistrering. Disabling not needed. Returning.")
        }
        minsideMicrofrontendService.enableMinsideMicrofrontend(behandling = behandling)
    }

    @Transactional
    fun enableAllMinsideMicrofrontends() {
        logger.debug("Enabling all minside microfrontends")
        val behandlinger = behandlingRepository.findByFeilregistreringIsNull()
        behandlinger.forEach {
            minsideMicrofrontendService.enableMinsideMicrofrontend(behandling = it)
        }
        logger.debug("Finished enabling all minside microfrontends")
    }

    @Transactional
    fun disableMinsideMicrofrontend(behandlingId: UUID) {
        val behandling = behandlingRepository.findByIdEager(behandlingId)
        minsideMicrofrontendService.disableMinsideMicrofrontend(behandling = behandling)
    }

    @Transactional
    fun disableAllMinsideMicrofrontends() {
        logger.debug("Disabling all minside microfrontends")
        val behandlinger = behandlingRepository.findAll()
        behandlinger.forEach {
            minsideMicrofrontendService.disableMinsideMicrofrontend(behandling = it)
        }
        logger.debug("Finished disabling all minside microfrontends")
    }

    @CacheEvict(
        cacheNames = [
            ENHET_CACHE,
            ENHETER_CACHE,
            KRR_INFO_CACHE,
            SAKSBEHANDLER_NAME_CACHE,
            POSTSTEDER_CACHE,
            LANDKODER_CACHE,
            PERSON_ADDRESS,
            DOK_DIST_KANAL,
            GOSYSOPPGAVE_GJELDER_CACHE,
            GOSYSOPPGAVE_OPPGAVETYPE_CACHE,
            GOSYSOPPGAVE_ENHETSMAPPER_CACHE,
            GOSYSOPPGAVE_ENHETSMAPPE_CACHE,
        ],
        allEntries = true
    )
    fun evictAllCaches() {
        logger.debug("Evicted all caches")
    }

    @Transactional
    fun setIdOnParterWithBehandlinger(behandlingIdList: List<UUID>) {
        logger.debug("setIdOnParterWithBehandlinger is called")
        val behandlingerSize = behandlingIdList.size
        logger.debug("Found $behandlingerSize behandlinger to set id on parter")

        val behandlinger = behandlingRepository.findAllById(behandlingIdList)

        behandlinger.forEach { behandling ->
            //Id for sakenGjelder is already set from Flyway-script.

            //then for klager. Set to same as sakenGjelder if klager is same person, (else keep what Flyway-script set)
            if (behandling.klager.partId.value == behandling.sakenGjelder.partId.value) {
                behandling.klager.id = behandling.sakenGjelder.id
            }

            if (behandling.prosessfullmektig?.partId?.value == behandling.klager.partId.value) {
                behandling.prosessfullmektig!!.id = behandling.klager.id
            }

            //these ids should then be set for brevmottakere also. Both from DUA-relation and from "forlenget behandlingstid"-relation
            if (behandling is BehandlingWithVarsletBehandlingstid) {
                if (behandling.forlengetBehandlingstidDraft != null) {
                    val getReceiversStart = System.currentTimeMillis()
                    val receivers = behandling.forlengetBehandlingstidDraft!!.receivers

                    receivers.forEach { receiver ->
                        when (receiver.identifikator) {
                            behandling.sakenGjelder.partId.value -> {
                                receiver.technicalPartId = behandling.sakenGjelder.id
                            }

                            behandling.klager.partId.value -> {
                                receiver.technicalPartId = behandling.klager.id
                            }

                            behandling.prosessfullmektig?.partId?.value -> {
                                receiver.technicalPartId = behandling.prosessfullmektig!!.id
                            }
                        }
                    }
                    logger.debug(
                        "Handling forlengetBehandlingstidDraft receivers for behandling (${behandling.id}) took ${System.currentTimeMillis() - getReceiversStart} millis. Found ${receivers.size} receivers"
                    )
                }
            }

            val startGetDUA = System.currentTimeMillis()
            val duaList = dokumentUnderArbeidRepository.findByBehandlingId(behandling.id)
            logger.debug(
                "Getting DUA (with eager brevmottakere) for behandling (${behandling.id}) took ${System.currentTimeMillis() - startGetDUA} millis. Found ${duaList.size} DUAs"
            )
            duaList.forEach { dokumentUnderArbeid ->
                if (dokumentUnderArbeid is DokumentUnderArbeidAsHoveddokument) {
                    val getReceiversStart = System.currentTimeMillis()
                    val receivers = dokumentUnderArbeid.brevmottakere

                    if (receivers.isNotEmpty()) {
                        receivers.forEach { receiver ->
                            when (receiver.identifikator) {
                                behandling.sakenGjelder.partId.value -> {
                                    receiver.technicalPartId = behandling.sakenGjelder.id
                                }

                                behandling.klager.partId.value -> {
                                    receiver.technicalPartId = behandling.klager.id
                                }

                                behandling.prosessfullmektig?.partId?.value -> {
                                    receiver.technicalPartId = behandling.prosessfullmektig!!.id
                                }
                            }
                        }
                        logger.debug(
                            "Handling dokumentUnderArbeid receivers for behandling (${behandling.id}) took ${System.currentTimeMillis() - getReceiversStart} millis. Found ${receivers.size} receivers"
                        )
                    }
                }
            }
        }

        logger.debug("setIdOnParterWithBehandlinger is done. Processed $behandlingerSize behandlinger")
    }

    @Transactional
    fun setPreviousBehandlingId(dryRun: Boolean) {
        logger.debug("setPreviousBehandlingId is called with dryRun={}", dryRun)

        var updatedCount = 0
        var skippedCount = 0
        var nullCount = 0
        var count = 0
        val total = behandlingRepository.count()

        behandlingRepository.findAllForAdminStreamed().use { streamed ->
            streamed.forEach { behandling ->
                val previousBehandlingId = when (behandling) {
                    is Klagebehandling -> null
                    is Ankebehandling -> {
                        behandling.previousBehandlingId
                    }

                    is Omgjoeringskravbehandling -> {
                        when (behandling) {
                            is OmgjoeringskravbehandlingBasedOnJournalpost -> null
                            is OmgjoeringskravbehandlingBasedOnKabalBehandling -> behandling.previousBehandlingId
                            else -> error("Unknown Omgjoeringskravbehandling subtype: ${behandling::class.java}")
                        }
                    }

                    is AnkeITrygderettenbehandling -> {
                        ankebehandlingRepository.findPreviousAnker(
                            sakenGjelder = behandling.sakenGjelder.partId.value,
                            kildeReferanse = behandling.kildeReferanse,
                            dateLimit = behandling.created,
                        ).firstOrNull()?.id
                    }

                    is BehandlingEtterTrygderettenOpphevet -> {
                        behandling.previousBehandlingId
                    }

                    is GjenopptakITrygderettenbehandling -> TODO()
                    is Gjenopptaksbehandling -> TODO()
                }

                if (previousBehandlingId != null) {
                    if (behandling.previousBehandlingId == null) {
                        logger.debug("Will set previousBehandlingId for behandling ${behandling.id} to $previousBehandlingId")
                        if (!dryRun) {
                            behandling.previousBehandlingId = previousBehandlingId
                            behandling.modified = LocalDateTime.now()
                            entityManager.flush()
                        }
                        updatedCount++
                    } else if (behandling.previousBehandlingId != previousBehandlingId) {
                        logger.warn("Previous behandling was already set but differs. Behandling: ${behandling.id}, set value: ${behandling.previousBehandlingId}, new value: $previousBehandlingId")
                    } else {
                        skippedCount++
                    }
                } else {
                    nullCount++
                }
                if (count++ % 100 == 0) {
                    logger.debug("Handled $count behandlinger out of ca. $total")
                }

                entityManager.detach(behandling)
            }
        }

        logger.debug("setPreviousBehandlingId is done. Updated $updatedCount behandlinger, skipped $skippedCount behandlinger that already had previousBehandlingId set, and $nullCount behandlinger had no previousBehandlingId to set.")
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun backfillPersongalleri() {
        val fs36KlagebehandlingIds = transactionTemplate.execute {
            klagebehandlingRepository.findByFagsystemAndFeilregistreringIsNull(Fagsystem.FS36).map { it.id }
        } ?: emptyList()

        logger.debug("Found {} FS36 klagebehandlinger for persongalleri backfill", fs36KlagebehandlingIds.size)

        var backfilledCount = 0
        var skippedCount = 0
        val batchSize = 100
        val totalBatches = (fs36KlagebehandlingIds.size + batchSize - 1) / batchSize

        fs36KlagebehandlingIds.chunked(batchSize).forEachIndexed { batchIndex, chunk ->
            try {
                val result = transactionTemplate.execute {
                    var bf = 0
                    var sk = 0
                    chunk.forEach { id ->
                        val klagebehandling = klagebehandlingRepository.findById(id).orElse(null) ?: return@forEach
                        val existing = sakPersongalleriRepository.findByFagsystemAndFagsakId(
                            fagsystem = klagebehandling.fagsystem,
                            fagsakId = klagebehandling.fagsakId,
                        )

                        if (existing.isEmpty()) {
                            try {
                                klagebehandlingService.populatePersongalleri(klagebehandling)
                                bf++
                                logger.debug("Backfilled persongalleri for klagebehandling {}", klagebehandling.id)
                            } catch (e: Exception) {
                                logger.warn("Failed to backfill persongalleri for klagebehandling {}", klagebehandling.id, e)
                            }
                        } else {
                            sk++
                        }
                    }
                    bf to sk
                } ?: (0 to 0)
                backfilledCount += result.first
                skippedCount += result.second
                logger.debug(
                    "Committed batch {}/{}: backfilled {}, skipped {}. Totals so far: backfilled {}, skipped {}",
                    batchIndex + 1,
                    totalBatches,
                    result.first,
                    result.second,
                    backfilledCount,
                    skippedCount,
                )
            } catch (e: Exception) {
                logger.warn("Failed to commit batch {}/{} for persongalleri backfill", batchIndex + 1, totalBatches, e)
            }
        }

        logger.debug("Backfill persongalleri done. Backfilled: {}, skipped (already exists): {}", backfilledCount, skippedCount)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun backfillPersonProtection() {
        val fnrList: Set<String> = transactionTemplate.execute {
            val fnrFromBehandlinger = behandlingRepository.findDistinctSakenGjelderPersonValues() //51100 in prod
            val fnrFromPersongalleri = sakPersongalleriRepository.findDistinctFoedselsnummer() //6140 after backfillPersongalleri is done
            fnrFromBehandlinger + fnrFromPersongalleri
        } ?: emptySet()

        val existingFnr = transactionTemplate.execute {
            personProtectionRepository.findAll().map { it.foedselsnummer }.toSet()
        } ?: emptySet()

        val missingFnr = fnrList.filter { it !in existingFnr }

        logger.debug(
            "Backfill person protection: {} unique sakenGjelder persons, {} already have protection, {} to backfill",
            fnrList.size,
            existingFnr.size,
            missingFnr.size,
        )

        var createdCount = 0
        var failedCount = 0
        val batchSize = 200
        val batches = missingFnr.chunked(batchSize)
        val totalBatches = batches.size

        batches.forEachIndexed { batchIndex, batch ->
            try {
                val personList = klageLookupGateway.getPersonBulk(fnrList = batch)

                val created = transactionTemplate.execute {
                    var c = 0
                    personList.forEach { person ->
                        personProtectionRepository.save(
                            PersonProtection(
                                foedselsnummer = person.foedselsnr,
                                fortrolig = person.fortrolig,
                                strengtFortrolig = person.strengtFortrolig || person.strengtFortroligUtland,
                                skjermet = person.egenAnsatt,
                            )
                        )
                        c++
                    }
                    c
                } ?: 0
                createdCount += created

                val returnedFnrs = personList.map { it.foedselsnr }.toSet()
                val missedInBatch = batch.count { it !in returnedFnrs }
                if (missedInBatch > 0) {
                    failedCount += missedInBatch
                }

                logger.debug(
                    "Committed person protection batch {}/{}: created {}, total so far: {}",
                    batchIndex + 1,
                    totalBatches,
                    created,
                    createdCount,
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to backfill person protection for batch {}/{} of {} persons",
                    batchIndex + 1,
                    totalBatches,
                    batch.size,
                    e,
                )
                failedCount += batch.size
            }
        }

        logger.debug("Backfill person protection done. Created: {}, failed/missed: {}", createdCount, failedCount)
    }
}