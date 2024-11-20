package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsMellomlagret
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.JournalfoertDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.service.InnholdsfortegnelseService
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemlerV2
import no.nav.klage.oppgave.api.view.TaskListMerkantilView
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.skjermede.SkjermedeApiClient
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.eventlisteners.StatistikkTilDVHService.Companion.TR_ENHET
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.util.*
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val journalfoertDokumentUnderArbeidAsVedleggRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
    private val kafkaEventRepository: KafkaEventRepository,
    private val fileApiClient: FileApiClient,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
    private val endringsloggRepository: EndringsloggRepository,
    private val skjermedeApiClient: SkjermedeApiClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
    private val safFacade: SafFacade,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingService: BehandlingService,
    private val mottakRepository: MottakRepository,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val tokenUtil: TokenUtil,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val taskListMerkantilRepository: TaskListMerkantilRepository,
    private val pdlFacade: PdlFacade,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = ourJacksonObjectMapper()
    }

    fun syncKafkaWithDb() {
        var pageable: Pageable =
            PageRequest.of(0, 50, Sort.by("created").descending())
        do {
            val behandlingPage = behandlingRepository.findAll(pageable)

            behandlingPage.content.map { behandling ->
                try {
                    when (behandling) {
                        is Klagebehandling ->
                            behandlingEndretKafkaProducer.sendKlageEndret(behandling)

                        is Ankebehandling ->
                            behandlingEndretKafkaProducer.sendAnkeEndret(behandling)

                        is AnkeITrygderettenbehandling ->
                            behandlingEndretKafkaProducer.sendAnkeITrygderettenEndret(behandling)

                        is BehandlingEtterTrygderettenOpphevet ->
                            behandlingEndretKafkaProducer.sendBehandlingOpprettetEtterTrygderettenOpphevet(behandling)

                        is Omgjoeringskravbehandling ->
                            behandlingEndretKafkaProducer.sendOmgjoeringskravEndret(behandling)
                    }
                } catch (e: Exception) {
                    logger.warn("Exception during send to Kafka", e)
                }
            }

            pageable = behandlingPage.nextPageable()
        } while (pageable.isPaged)
    }

    fun reindexBehandlingInSearch(behandlingId: UUID) {
        when (val behandling = behandlingRepository.findByIdEager(behandlingId)) {
            is Klagebehandling ->
                behandlingEndretKafkaProducer.sendKlageEndret(behandling)

            is Ankebehandling ->
                behandlingEndretKafkaProducer.sendAnkeEndret(behandling)

            is AnkeITrygderettenbehandling ->
                behandlingEndretKafkaProducer.sendAnkeITrygderettenEndret(behandling)

            is BehandlingEtterTrygderettenOpphevet ->
                behandlingEndretKafkaProducer.sendBehandlingOpprettetEtterTrygderettenOpphevet(behandling)

            is Omgjoeringskravbehandling ->
                behandlingEndretKafkaProducer.sendOmgjoeringskravEndret(behandling)
        }
    }

    /** only for use in dev */
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

        endringsloggRepository.deleteAll(endringsloggRepository.findByBehandlingIdOrderByTidspunktDesc(behandlingId))

        //delete mottak also
        val behandling = behandlingRepository.findById(behandlingId).get()
        val mottakId = if (behandling is Klagebehandling) {
            behandling.mottakId
        } else if (behandling is Ankebehandling && behandling.mottakId != null) {
            behandling.mottakId
        } else null

        behandlingRepository.deleteById(behandlingId)

        if (mottakId != null) {
            mottakRepository.deleteById(mottakId)
        }

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

    fun resendToDVH() {
        logger.debug("Attempting to resend all events to DVH")
        kafkaDispatcher.dispatchEventsToKafka(
            EventType.STATS_DVH,
            listOf(UtsendingStatus.IKKE_SENDT, UtsendingStatus.FEILET, UtsendingStatus.SENDT)
        )
    }

    fun generateMissingAnkeITrygderetten() {
        logger.debug("Attempting generate missing AnkeITrygderettenBehandling")

        val candidates =
            ankebehandlingRepository.findByFerdigstillingAvsluttetIsNotNullAndFeilregistreringIsNullAndUtfallIn(
                utfallToTrygderetten
            )

        val existingAnkeITrygderettenBehandlingKildereferanseAndFagsystem =
            ankeITrygderettenbehandlingRepository.findAll().map { it.kildeReferanse to it.fagsystem }

        val ankebehandlingerWithouthAnkeITrygderetten =
            candidates.filter { it.kildeReferanse to it.fagsystem !in existingAnkeITrygderettenBehandlingKildereferanseAndFagsystem }

        val ankebehandlingerWithAnkeITrygderetten =
            candidates.filter { it.kildeReferanse to it.fagsystem in existingAnkeITrygderettenBehandlingKildereferanseAndFagsystem }

        var logString = ""

        logString += "Antall kandidater blant Ankebehandlinger: ${candidates.size} \n"

        logString += "Antall manglende ankeITrygderetten: ${ankebehandlingerWithouthAnkeITrygderetten.size} \n"
        logString += "Antall tidligere opprettede ankeITrygderetten: ${ankebehandlingerWithAnkeITrygderetten.size} \n\n"

        ankebehandlingerWithouthAnkeITrygderetten.forEach {
            try {
                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(
                    it.createAnkeITrygderettenbehandlingInput()
                )
                logString += "Mangler: ankeBehandlingId: ${it.id},  kildeReferanse: ${it.kildeReferanse} \n"
            } catch (e: Exception) {
                logger.warn(
                    "Klarte ikke å opprette ankeITrygderettenbehandling basert på ankebehandling ${it.id}. Undersøk!",
                    e
                )
            }
        }

        ankebehandlingerWithAnkeITrygderetten.forEach {
            logString += "Finnes fra før: ankeBehandlingId: ${it.id},  kildeReferanse: ${it.kildeReferanse} \n"
        }

        val existingAnkeITrygderettenBehandlingKildereferanseAndFagsystemAfter =
            ankeITrygderettenbehandlingRepository.findAll().map { it.kildeReferanse to it.fagsystem }

        val ankebehandlingerWithouthAnkeITrygderettenAfter =
            candidates.filter { it.kildeReferanse to it.fagsystem !in existingAnkeITrygderettenBehandlingKildereferanseAndFagsystemAfter }

        val ankebehandlingerWithAnkeITrygderettenAfter =
            candidates.filter { it.kildeReferanse to it.fagsystem in existingAnkeITrygderettenBehandlingKildereferanseAndFagsystemAfter }

        logString += "Antall manglende ankeITrygderetten etter operasjonen: ${ankebehandlingerWithouthAnkeITrygderettenAfter.size} \n"
        logString += "Antall opprettede ankeITrygderetten etter operasjonen: ${ankebehandlingerWithAnkeITrygderettenAfter.size} \n"

        logger.debug(logString)
    }

    fun isSkjermet(fnr: String) {
        try {
            logger.debug("isSkjermet called")
            val isSkjermet = skjermedeApiClient.isSkjermet(fnr)

            secureLogger.debug("isSkjermet: {} for fnr {}", isSkjermet, fnr)
        } catch (e: Exception) {
            secureLogger.error("isSkjermet failed for fnr $fnr", e)
        }
    }

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

        secureLogger.debug("Expired, assigned saksbehandler: \n $saksbehandlerLogOutput")

        val medunderskriverSet = unfinishedBehandlinger.mapNotNull { it.medunderskriver?.saksbehandlerident }.toSet()
        var medunderskriverLogOutput = ""
        medunderskriverSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                medunderskriverLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        secureLogger.debug("Expired, assigned medunderskriver: \n $medunderskriverLogOutput")

        val rolSet = unfinishedBehandlinger.mapNotNull { it.rolIdent }.toSet()
        var rolLogOutput = ""
        rolSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                rolLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        secureLogger.debug("Expired, assigned rol: \n $rolLogOutput")
    }

    fun logProtected() {
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        secureLogger.debug("Checking for protected users")
        unfinishedBehandlinger.forEach { behandling ->
            if (behandling.sakenGjelder.partId.type == PartIdType.PERSON) {
                try {
                    val person = pdlFacade.getPersonInfo(behandling.sakenGjelder.partId.value)
                    if (person.harBeskyttelsesbehovStrengtFortrolig()) {
                        secureLogger.debug("Protected user in behandling with id {}", behandling.id)
                    }
                } catch (e: Exception) {
                    secureLogger.debug("Couldn't check person, exception: $e")
                }
            }
        }
    }


    @Scheduled(cron = "\${SETTINGS_CLEANUP_CRON}", zone = "Europe/Oslo")
    @SchedulerLock(name = "cleanupExpiredAssignees")
    fun cleanupExpiredAssignees() {
        logger.info("Running scheduled expired assignee check.")
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
        unfinishedBehandlinger.forEach {
            val assignedMedunderskriver = it.medunderskriver?.saksbehandlerident
            if (assignedMedunderskriver != null) {
                if (checkIfAssigneeIsExpired(navIdent = assignedMedunderskriver)) {
                    logger.info("Behandling ${it.id} has expired medunderskriver: $assignedMedunderskriver, setting to null.")
                    behandlingService.setMedunderskriverToNullInSystemContext(it.id)
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

    fun logInvalidRegistreringshjemler() {
        val unfinishedBehandlinger = behandlingRepository.findByFerdigstillingIsNull()
        val ytelseAndHjemmelPairSet = unfinishedBehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, invalidHjemler) = ytelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseTilRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredInvalidHjemler = invalidHjemler.filter { it.second.isNotEmpty() }
        secureLogger.debug("Invalid registreringshjemler in unfinished behandlinger: {}", filteredInvalidHjemler)

        val klagebehandlinger = klagebehandlingRepository.findByKakaKvalitetsvurderingVersionIs(2)
        val klageYtelseAndHjemmelPairSet = klagebehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, klageinvalidHjemler) = klageYtelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseTilRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredKlageInvalidHjemler = klageinvalidHjemler.filter { it.second.isNotEmpty() }
        secureLogger.debug("Invalid registreringshjemler in klagebehandlinger v2: {}", filteredKlageInvalidHjemler)

        val ankebehandlinger = ankebehandlingRepository.findByKakaKvalitetsvurderingVersionIs(2)
        val ankeYtelseAndHjemmelPairSet = ankebehandlinger.map { it.ytelse to it.registreringshjemler }.toSet()
        val (_, ankeinvalidHjemler) = ankeYtelseAndHjemmelPairSet.partition { pair ->
            pair.second.any { ytelseTilRegistreringshjemlerV2[pair.first]?.contains(it) ?: false }
        }
        val filteredAnkeInvalidHjemler = ankeinvalidHjemler.filter { it.second.isNotEmpty() }
        secureLogger.debug("Invalid registreringshjemler in ankebehandlinger v2: {}", filteredAnkeInvalidHjemler)
    }

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

    fun getTaskListMerkantil(): List<TaskListMerkantilView> {
        return taskListMerkantilRepository.findAll().sortedByDescending { it.created }.map { it.toTaskListMerkantilView() }
    }

    fun TaskListMerkantil.toTaskListMerkantilView(): TaskListMerkantilView {
        return TaskListMerkantilView(
            id = id,
            behandlingId = behandlingId,
            reason = reason,
            created = created,
            dateHandled = dateHandled,
            handledBy = handledBy,
            handledByName = handledByName,
            comment = comment,
            typeId = behandlingRepository.findByIdEager(behandlingId).type.id
        )
    }
}



fun migrateTables(fromJsonString: String?, secureLogger: Logger?): String {
    secureLogger?.debug("fromJsonString: $fromJsonString")

    val jsonNode = jacksonObjectMapper().readTree(fromJsonString)
    val tableNodes = traverseNodesAndGetTables(jsonNode, mutableSetOf())

    if (tableNodes.size > 1) {
        secureLogger?.debug("fromJsonString had more than one table: ${tableNodes.size}")
    }

    for (tableNode in tableNodes) {
        val tableChildren = tableNode.path("children") as ArrayNode

        if (tableChildren.size() > 1 || tableChildren.first().path("type").asText() != "tbody") {
            break
        }

        val tbodyChildrenToMove = tableChildren.first().path("children") as ArrayNode
        tableChildren.addAll(tbodyChildrenToMove)
        val indexOfTbody = tableChildren.indexOfFirst { it.path("type").asText() == "tbody" }
        tableChildren.remove(indexOfTbody)
    }

    val newJsonString = jsonNode.toPrettyString()

    secureLogger?.debug("toJsonString: $newJsonString")

    return newJsonString
}

fun traverseNodesAndGetTables(root: JsonNode, tableSet: MutableSet<JsonNode>): Set<JsonNode> {
    if (root.isObject) {
        val fieldNames = root.fieldNames().asSequence().toList()
        fieldNames.forEach {
            val fieldValue: JsonNode = root.get(it)

            if (fieldValue.asText() == "table") {
                tableSet.add(root)
            }

            traverseNodesAndGetTables(fieldValue, tableSet)
        }
    } else if (root.isArray) {
        val arrayNode: ArrayNode = root as ArrayNode
        for (i in 0 until arrayNode.size()) {
            val arrayElement: JsonNode = arrayNode.get(i)
            traverseNodesAndGetTables(arrayElement, tableSet)
        }
    } else {
        // JsonNode root represents a single value field
    }

    return tableSet
}