package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsMellomlagret
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.JournalfoertDokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.service.InnholdsfortegnelseService
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemlerV2
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.skjermede.SkjermedeApiClient
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.eventlisteners.StatistikkTilDVHService.Companion.TR_ENHET
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.getSortKey
import org.slf4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    }

    fun syncKafkaWithDb() {
        var pageable: Pageable =
            PageRequest.of(0, 50, Sort.by("created").descending())
        do {
            val behandlingPage = behandlingRepository.findAll(pageable)

            behandlingPage.content.map { behandling ->
                try {
                    when (behandling.type) {
                        Type.KLAGE ->
                            behandlingEndretKafkaProducer.sendKlageEndretV2(behandling as Klagebehandling)

                        Type.ANKE ->
                            behandlingEndretKafkaProducer.sendAnkeEndretV2(behandling as Ankebehandling)

                        Type.ANKE_I_TRYGDERETTEN ->
                            behandlingEndretKafkaProducer.sendAnkeITrygderettenEndretV2(behandling as AnkeITrygderettenbehandling)
                    }
                } catch (e: Exception) {
                    logger.warn("Exception during send to Kafka", e)
                }
            }

            pageable = behandlingPage.nextPageable()
        } while (pageable.isPaged)
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

        behandlingRepository.deleteById(behandlingId)

        //Delete in search
        behandlingEndretKafkaProducer.sendBehandlingDeleted(behandlingId)

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
            ankebehandlingRepository.findByAvsluttetIsNotNullAndFeilregistreringIsNullAndUtfallIn(
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
        val events = kafkaEventRepository.getAllByTypeIsLike(EventType.STATS_DVH)

        val filteredEvents = events.filter {
            val parsedStatistikkTilDVH = objectMapper.readValue(it.jsonPayload, StatistikkTilDVH::class.java)
            parsedStatistikkTilDVH.behandlingType == "Anke" &&
                    parsedStatistikkTilDVH.behandlingStatus in listOf(
                BehandlingState.AVSLUTTET,
                BehandlingState.NY_ANKEBEHANDLING_I_KA
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
        val unfinishedBehandlinger = behandlingRepository.findByAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull()
        val saksbehandlerSet = unfinishedBehandlinger.mapNotNull { it.tildeling?.saksbehandlerident }.toSet()
        var saksbehandlerLogOutput = ""
        saksbehandlerSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                saksbehandlerLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        secureLogger.debug("Expired, assigned saksbehandler: \n $saksbehandlerLogOutput" )

        val medunderskriverSet = unfinishedBehandlinger.mapNotNull { it.medunderskriver?.saksbehandlerident }.toSet()
        var medunderskriverLogOutput = ""
        medunderskriverSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                medunderskriverLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        secureLogger.debug("Expired, assigned medunderskriver: \n $medunderskriverLogOutput" )

        val rolSet = unfinishedBehandlinger.mapNotNull { it.rolIdent }.toSet()
        var rolLogOutput = ""
        rolSet.forEach {
            val nomInfo = saksbehandlerService.getAnsattInfoFromNom(it)
            if (nomInfo.data?.ressurs?.sluttdato?.isBefore(LocalDate.now().minusWeeks(1)) == true) {
                rolLogOutput += "Sluttdato is in the past: $it \n"
            }
        }

        secureLogger.debug("Expired, assigned rol: \n $rolLogOutput" )
    }

    fun logInvalidRegistreringshjemler() {
        val unfinishedBehandlinger = behandlingRepository.findByAvsluttetAvSaksbehandlerIsNull()
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