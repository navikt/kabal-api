package no.nav.klage.oppgave.service

import jakarta.persistence.EntityManager
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsMellomlagret
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.JournalfoertDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.service.InnholdsfortegnelseService
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToRegistreringshjemlerV2
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.clients.klagenotificationsapi.KlageNotificationsApiClient
import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.DOK_DIST_KANAL
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ENHETER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ENHET_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_ENHETSMAPPER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_ENHETSMAPPE_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_GJELDER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GOSYSOPPGAVE_OPPGAVETYPE_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.GROUPMEMBERS_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.KRR_INFO_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.LANDKODER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.PERSON_ADDRESS
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.POSTSTEDER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ROLLER_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.SAKSBEHANDLERE_I_ENHET_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.SAKSBEHANDLER_NAME_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.TILGANGER_CACHE
import no.nav.klage.oppgave.config.SchedulerHealthGate
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.service.StatistikkTilDVHService.Companion.TR_ENHET
import no.nav.klage.oppgave.util.*
import no.nav.slackposter.SlackClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.InetAddress
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
    private val journalfoertDokumentUnderArbeidAsVedleggRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
    private val kafkaEventRepository: KafkaEventRepository,
    private val fileApiClient: FileApiClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
    private val safFacade: SafFacade,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingService: BehandlingService,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val tokenUtil: TokenUtil,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val personService: PersonService,
    private val minsideMicrofrontendService: MinsideMicrofrontendService,
    private val egenAnsattService: EgenAnsattService,
    private val slackClient: SlackClient,
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val personCacheService: PersonCacheService,
    private val entityManager: EntityManager,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val klageNotificationsApiClient: KlageNotificationsApiClient,
    private val schedulerHealthGate: SchedulerHealthGate,
) {

    @Value("\${KLAGE_BACKEND_GROUP_ID}")
    lateinit var klageBackendGroupId: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper = ourJacksonObjectMapper()
    }

    @Transactional
    fun syncKafkaWithDb() {
        var pageable: Pageable =
            PageRequest.of(0, 50, Sort.by("created").descending())
        do {
            val behandlingPage = behandlingRepository.findAll(pageable)

            behandlingPage.content.map { behandling ->
                try {
                    behandlingEndretKafkaProducer.sendBehandlingEndret(behandling)
                } catch (e: Exception) {
                    logger.warn("Exception during send to Kafka", e)
                }
            }

            pageable = behandlingPage.nextPageable()
        } while (pageable.isPaged)
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
            val parsedStatistikkTilDVH = objectMapper.readValue(it.jsonPayload, StatistikkTilDVH::class.java)
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
                var parsedStatistikkTilDVH = objectMapper.readValue(it.jsonPayload, StatistikkTilDVH::class.java)
                parsedStatistikkTilDVH = parsedStatistikkTilDVH.copy(
                    ansvarligEnhetKode = TR_ENHET,
                    tekniskTid = LocalDateTime.now()
                )
                it.jsonPayload = objectMapper.writeValueAsString(parsedStatistikkTilDVH)
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
        var saksbehandlerLogOutput = ""
        saksbehandlerSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                saksbehandlerLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        logger.debug("Expired, assigned saksbehandler: \n $saksbehandlerLogOutput")

        val medunderskriverSet = unfinishedBehandlinger.mapNotNull { it.medunderskriver?.saksbehandlerident }.toSet()
        var medunderskriverLogOutput = ""
        medunderskriverSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                medunderskriverLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        logger.debug("Expired, assigned medunderskriver: \n $medunderskriverLogOutput")

        val rolSet = unfinishedBehandlinger.mapNotNull { it.rolIdent }.toSet()
        var rolLogOutput = ""
        rolSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
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
                    val person = personService.getPersonInfo(behandling.sakenGjelder.partId.value)
                    if (person.harBeskyttelsesbehovStrengtFortrolig()) {
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
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullWithHjemler()
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
        val sakenGjelderFnrList = unfinishedBehandlinger
            .filter { it.sakenGjelder.partId.type == PartIdType.PERSON }
            .map { it.sakenGjelder.partId.value }
            .distinct()

        val pdlStart = System.currentTimeMillis()
        personService.fillPersonCache(fnrList = sakenGjelderFnrList)
        val now = System.currentTimeMillis()
        logger.debug("Time it took to fill person cache: ${now - pdlStart} millis")

        unfinishedBehandlinger.forEach { behandling ->
            if (behandling.sakenGjelder.partId.type == PartIdType.PERSON) {
                try {
                    val person = personService.getPersonInfo(behandling.sakenGjelder.partId.value)
                    if (person.harBeskyttelsesbehovStrengtFortrolig()) {
                        strengtFortroligBehandlinger.add(behandling.id.toString())
                    }

                    if (person.harBeskyttelsesbehovFortrolig()) {
                        behandling.tildeling?.saksbehandlerident?.let {
                            if (!saksbehandlerService.hasFortroligRole(ident = it, useCache = true)) {
                                fortroligBehandlinger.add(behandling.id.toString())
                            }
                        }
                    }

                    if (egenAnsattService.erEgenAnsatt(person.foedselsnr)) {
                        behandling.tildeling?.saksbehandlerident?.let {
                            if (!saksbehandlerService.hasEgenAnsattRole(ident = it, useCache = true)) {
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
            unfinishedBehandlingerInput ?: behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNullWithHjemler()
        val start = System.currentTimeMillis()
        val unavailableBehandlinger = mutableSetOf<Behandling>()
        val missingHjemmelInRegistryBehandling = mutableSetOf<Pair<UUID, Set<Hjemmel>>>()
        val ytelseToHjemlerMap = mutableMapOf<Ytelse, Set<Hjemmel>>()

        unfinishedBehandlinger.forEach { behandling ->
            when (behandling) {
                is Klagebehandling, is Ankebehandling, is Omgjoeringskravbehandling -> {
                    if (behandling.tildeling == null) {
                        if (!ytelseToHjemlerMap.containsKey(behandling.ytelse)) {
                            ytelseToHjemlerMap[behandling.ytelse] = kabalInnstillingerService.getRegisteredHjemlerForYtelse(behandling.ytelse)
                        }
                        val hjemlerForYtelseInInnstillinger = ytelseToHjemlerMap[behandling.ytelse]!!
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

    fun emptyPersonCache() {
        personCacheService.emptyCache()
    }

    fun resetPersonCacheFromOpenBehandlinger() {
        val start = System.currentTimeMillis()
        emptyPersonCache()
        logger.debug("Emptied person cache from admin endpoint in pod ${InetAddress.getLocalHost().hostName}, took ${System.currentTimeMillis() - start} ms")

        val allOpenBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        logger.debug("Found all open behandlinger: ${allOpenBehandlinger.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        val allSakenGjelderFnr = allOpenBehandlinger.filter { it.sakenGjelder.partId.type == PartIdType.PERSON }
            .map { it.sakenGjelder.partId.value }
            .distinct()

        val allKlagerFnr = allOpenBehandlinger.filter { it.klager.partId.type == PartIdType.PERSON }
            .map { it.klager.partId.value }
            .distinct()

        val allFullmektigFnr = allOpenBehandlinger.filter { it.prosessfullmektig?.partId?.type == PartIdType.PERSON }
            .map { it.prosessfullmektig?.partId?.value }
            .distinct()

        val allPersonsInOpenBehandlingerFnr =
            (allSakenGjelderFnr + allKlagerFnr + allFullmektigFnr).filterNotNull().distinct()

        logger.debug("Found all distinct persons: ${allPersonsInOpenBehandlingerFnr.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        personService.fillPersonCache(allPersonsInOpenBehandlingerFnr)

        logger.debug("Finished inserting all persons from open behandlinger in cache in ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
    }

    fun resetPersonCacheFromAllBehandlinger() {
        val start = System.currentTimeMillis()
        emptyPersonCache()
        logger.debug("Emptied person cache from admin endpoint in pod ${InetAddress.getLocalHost().hostName}, took ${System.currentTimeMillis() - start} ms")

        val allBehandlinger = behandlingRepository.findAll()
        logger.debug("Found all behandlinger: ${allBehandlinger.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        val allSakenGjelderFnr = allBehandlinger.filter { it.sakenGjelder.partId.type == PartIdType.PERSON }
            .map { it.sakenGjelder.partId.value }
            .distinct()

        val allKlagerFnr = allBehandlinger.filter { it.klager.partId.type == PartIdType.PERSON }
            .map { it.klager.partId.value }
            .distinct()

        val allFullmektigFnr = allBehandlinger.filter { it.prosessfullmektig?.partId?.type == PartIdType.PERSON }
            .map { it.prosessfullmektig?.partId?.value }
            .distinct()

        val allPersonsInAllBehandlingerFnr =
            (allSakenGjelderFnr + allKlagerFnr + allFullmektigFnr).filterNotNull().distinct()

        logger.debug("Found all distinct persons: ${allPersonsInAllBehandlingerFnr.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        personService.fillPersonCache(allPersonsInAllBehandlingerFnr)

        logger.debug("Finished inserting all persons in cache in ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
    }

    @Transactional
    @Scheduled(cron = "\${SETTINGS_CLEANUP_CRON}", zone = "Europe/Oslo")
    @SchedulerLock(name = "cleanupExpiredAssignees")
    fun cleanupExpiredAssignees() {
        if (!schedulerHealthGate.isReady()) return
        logger.info("Running scheduled expired assignee check.")
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        unfinishedBehandlinger.forEach {
            val assignedMedunderskriver = it.medunderskriver?.saksbehandlerident
            if (assignedMedunderskriver != null) {
                if (checkIfAssigneeIsExpired(navIdent = assignedMedunderskriver)) {
                    logger.info("Behandling ${it.id} has expired medunderskriver: $assignedMedunderskriver, setting to null.")
                    behandlingService.setMedunderskriverAndMedunderskriverFlowToNull(behandlingId = it.id, systemUserContext = true)
                }
            }

            val assignedRol = it.rolIdent
            if (assignedRol != null) {
                if (checkIfAssigneeIsExpired(navIdent = assignedRol)) {
                    logger.info("Behandling ${it.id} has expired rol: $assignedRol, setting to null.")
                    behandlingService.setRolToNullInSystemContext(it.id)
                }
            }

            val assignedSaksbehandler = it.tildeling?.saksbehandlerident
            if (assignedSaksbehandler != null) {
                if (checkIfAssigneeIsExpired(navIdent = assignedSaksbehandler)) {
                    logger.info("Behandling ${it.id} has expired tildeling: $assignedSaksbehandler, setting to null.")
                    behandlingService.setExpiredTildeltSaksbehandlerToNullInSystemContext(it.id)
                }
            }
        }

        logger.info("Scheduled expired assignee check completed.")
    }

    private fun checkIfAssigneeIsExpired(navIdent: String): Boolean {
        val nomInfo = saksbehandlerService.getAnsattInfoFromNom(navIdent = navIdent)
        return nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true
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
    fun setSortKeyToDUA() {
        val allDUAs = journalfoertDokumentUnderArbeidAsVedleggRepository.findAll()
        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = allDUAs.map { it.journalpostId }.toSet(),
            fnr = null,
            saksbehandlerContext = false,
        )
        var keys = ""
        allDUAs.forEach { dua ->
            val journalpostInDokarkiv = journalpostList.find { it.journalpostId == dua.journalpostId }!!
            val sortKey = getSortKey(
                journalpost = journalpostInDokarkiv,
                dokumentInfoId = dua.dokumentInfoId
            )
            keys += sortKey + "\n"
            dua.sortKey = sortKey
        }
        logger.debug("setSortKeyToDUA: ${allDUAs.size} DUAs were updated with sortKeys: $keys")
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
            TILGANGER_CACHE,
            ROLLER_CACHE,
            SAKSBEHANDLERE_I_ENHET_CACHE,
            GROUPMEMBERS_CACHE,
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
                        behandling.sourceBehandlingId
                    }

                    is Omgjoeringskravbehandling -> {
                        when (behandling) {
                            is OmgjoeringskravbehandlingBasedOnJournalpost -> null
                            is OmgjoeringskravbehandlingBasedOnKabalBehandling -> behandling.sourceBehandlingId
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
                        behandling.sourceBehandlingId
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
}