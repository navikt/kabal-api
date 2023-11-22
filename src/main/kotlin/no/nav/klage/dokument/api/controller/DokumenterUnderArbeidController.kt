package no.nav.klage.dokument.api.controller


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.api.view.DokumentUnderArbeidMetadata
import no.nav.klage.oppgave.clients.events.KafkaEventClient
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.domain.kafka.Event
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.*

@RestController
@Tag(name = "kabal-api-dokumenter")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/dokumenter")
class DokumentUnderArbeidController(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentMapper: DokumentMapper,
    private val dokumentInputMapper: DokumentInputMapper,
    private val kafkaEventClient: KafkaEventClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping
    fun findDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        return dokumentUnderArbeidService.findDokumenterNotFinishedNew(behandlingId = behandlingId)
    }

    @PostMapping("/fil")
    fun createAndUploadHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @ModelAttribute input: FilInput
    ): DokumentView {
        logger.debug("Kall mottatt på createAndUploadHoveddokument")
        val opplastetFil = dokumentInputMapper.mapToMellomlagretDokument(
            multipartFile = input.file,
            tittel = input.file.originalFilename,
            dokumentType = DokumentType.of(input.dokumentTypeId),
        )
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.createOpplastetDokumentUnderArbeid(
                behandlingId = behandlingId,
                dokumentType = DokumentType.of(input.dokumentTypeId),
                opplastetFil = opplastetFil,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = opplastetFil.title,
                parentId = input.parentId,
            ),
            journalpost = null,
        )
    }

    @PostMapping("/journalfoertedokumenter")
    fun addJournalfoerteDokumenterAsVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: JournalfoerteDokumenterInput
    ): JournalfoerteDokumenterResponse {
        logger.debug("Kall mottatt på addJournalfoerteDokumenterAsVedlegg")
        return dokumentUnderArbeidService.handleJournalfoerteDokumenterAsVedlegg(
            behandlingId = behandlingId,
            journalfoerteDokumenterInput = input,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
    }

    @PutMapping("/{dokumentId}/dokumenttype")
    fun endreDokumentType(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: DokumentTypeInput
    ): DokumentView {
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.updateDokumentType(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                dokumentType = DokumentType.of(input.dokumentTypeId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ),
            journalpost = null,
        )
    }

    @ResponseBody
    @GetMapping("/{dokumentId}/pdf")
    fun getPdf(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getPdf for {}", dokumentId)
        return dokumentMapper.mapToByteArray(
            dokumentUnderArbeidService.getFysiskDokument(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @GetMapping("/{dokumentId}", "/{dokumentId}/title")
    fun getMetadata(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): DokumentUnderArbeidMetadata {
        logger.debug("Kall mottatt på getMetadata for {}", dokumentId)

        return DokumentUnderArbeidMetadata(
            behandlingId = behandlingId,
            documentId = dokumentId,
            title = dokumentUnderArbeidService.getDokumentUnderArbeid(dokumentId).name
        )
    }

    @GetMapping("/{dokumentId}/vedleggsoversikt")
    fun getMetadataForInnholdsfortegnelse(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): DokumentUnderArbeidMetadata {
        logger.debug("Kall mottatt på getMetadataForInnholdsfortegnelse for {}", dokumentId)

        return DokumentUnderArbeidMetadata(
            behandlingId = behandlingId,
            documentId = dokumentId,
            title = "Vedleggsoversikt"
        )
    }

    @GetMapping("/{hoveddokumentId}/vedleggsoversikt/pdf")
    @ResponseBody
    fun getInnholdsfortegnelsePdf(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("hoveddokumentId") hoveddokumentId: UUID,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getInnholdsfortegnelsePdf for {}", hoveddokumentId)
        return dokumentMapper.mapToByteArray(
            dokumentUnderArbeidService.getInnholdsfortegnelseAsFysiskDokument(
                behandlingId = behandlingId,
                hoveddokumentId = hoveddokumentId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{dokumentId}")
    fun deleteDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ) {
        logger.debug("Kall mottatt på deleteDokument for {}", dokumentId)
        dokumentUnderArbeidService.slettDokument(
            dokumentId = dokumentId,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
    }

    @PutMapping("/{dokumentId}/parent")
    fun kobleEllerFrikobleVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") persistentDokumentId: UUID,
        @RequestBody input: OptionalPersistentDokumentIdInput
    ): DokumentViewWithList {
        logger.debug("Kall mottatt på kobleEllerFrikobleVedlegg for {}", persistentDokumentId)
        try {
            return dokumentUnderArbeidService.kobleEllerFrikobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentId = persistentDokumentId,
                input = input
            )
        } catch (e: Exception) {
            logger.error("Feilet under kobling av dokument $persistentDokumentId med ${input.dokumentId}", e)
            throw e
        }
    }

    @PostMapping("/{dokumentid}/ferdigstill")
    fun idempotentOpprettOgFerdigstillDokumentEnhetFraHovedDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
        @RequestBody(required = true) input: FerdigstillDokumentInput,
    ): DokumentView {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.finnOgMarkerFerdigHovedDokument(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                ident = ident,
                brevmottakerIdents = input.brevmottakerIds,
            ),
            journalpost = null
        )
    }

    @GetMapping("/{dokumentid}/validate")
    fun validateDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
    ): List<DocumentValidationResponse> {
        //TODO only called for hoveddokumenter?
        return dokumentUnderArbeidService.validateIfSmartDokument(dokumentId)
    }

    //Old event stuff. Clients should read from EventController instead, and this can be deleted.
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun documentEvents(
        @PathVariable("behandlingId") behandlingId: String,
        @RequestParam("lastEventIdInput", required = false) lastEventIdInput: UUID?,
        request: HttpServletRequest,
    ): Flux<ServerSentEvent<String>> {
        logger.debug("Kall mottatt på documentEvents for behandlingId $behandlingId")

        //https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<String>> = Flux.interval(Duration.ofSeconds(10))
            .takeWhile { true }
            .map { tick -> toHeartBeatServerSentEvent(tick) }

        return kafkaEventClient.getEventPublisher()
            .mapNotNull { event -> jsonToEvent(event.data()) }
            .filter { Objects.nonNull(it) }
            .filter { it.behandlingId == behandlingId && it.name == "finished" }
            .mapNotNull { eventToServerSentEvent(it) }
            .mergeWith(heartbeatStream)
    }

    private fun toHeartBeatServerSentEvent(tick: Long): ServerSentEvent<String> {
        return eventToServerSentEvent(
            Event(
                behandlingId = "",
                id = "",
                name = "heartbeat-event-$tick",
                data = ""
            )
        )
    }

    private fun eventToServerSentEvent(event: Event): ServerSentEvent<String> {
        return ServerSentEvent.builder<String>()
            .id(event.id)
            .event(event.name)
            .data(event.data)
            .build()
    }

    private fun jsonToEvent(json: String?): Event {
        val event = jacksonObjectMapper().readValue(json, Event::class.java)
        logger.debug("Received event from Kafka: {}", event)
        return event
    }

    @PutMapping("/{dokumentid}/tittel")
    fun changeDocumentTitle(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
        @RequestBody input: DokumentTitleInput,
    ): DokumentView {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.updateDokumentTitle(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                dokumentTitle = input.title,
                innloggetIdent = ident,
            ),
            journalpost = null
        )
    }
}