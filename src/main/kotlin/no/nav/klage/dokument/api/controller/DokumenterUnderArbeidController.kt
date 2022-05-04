package no.nav.klage.dokument.api.controller


import io.swagger.annotations.Api
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentId
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentType
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Brevmottakertype
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.servlet.http.HttpServletRequest
import kotlin.concurrent.timer

@RestController
@Api(tags = ["kabal-api-dokumenter"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/dokumenter")
class DokumentUnderArbeidController(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerRepository,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val dokumentMapper: DokumentMapper,
    private val dokumenInputMapper: DokumentInputMapper,
    private val behandlingService: BehandlingService,
    @Value("\${EVENT_DELAY_SECONDS}") private val eventDelay: Long,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/fil")
    fun createAndUploadHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @ModelAttribute input: FilInput
    ): DokumentView {
        logger.debug("Kall mottatt på createAndUploadHoveddokument")
        val opplastetFil = dokumenInputMapper.mapToMellomlagretDokument(input.file, input.tittel, DokumentType.VEDTAK)
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                behandlingId = behandlingId,
                dokumentType = DokumentType.VEDTAK,
                opplastetFil = opplastetFil,
                json = null,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = opplastetFil.title,
            )
        )
    }

    @PostMapping("/smart")
    fun createSmartHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody body: SmartHovedDokumentInput,
    ): DokumentView {
        logger.debug("Kall mottatt på createSmartHoveddokument")
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                behandlingId = behandlingId,
                dokumentType = if (body.dokumentTypeId != null) DokumentType.of(body.dokumentTypeId) else DokumentType.VEDTAK,
                opplastetFil = null,
                json = body.json,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = body.tittel ?: DokumentType.VEDTAK.defaultFilnavn,
            )
        )
    }

    @PutMapping("/{dokumentId}/dokumenttype")
    fun endreDokumentType(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: DokumentTypeInput
    ): DokumentView {
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.updateDokumentType(
                behandlingId = behandlingId,
                dokumentId = DokumentId(dokumentId),
                dokumentType = DokumentType.of(input.dokumentTypeId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    //TODO: Har hoppet over endepunkter for å oppdatere/erstatte dokumentet

    @ResponseBody
    @GetMapping("/{dokumentId}/pdf")
    fun getPdf(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getPdf for $dokumentId")
        return dokumentMapper.mapToByteArray(
            dokumentUnderArbeidService.hentMellomlagretDokument(
                behandlingId = behandlingId,
                dokumentId = DokumentId(dokumentId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{dokumentId}")
    fun deleteDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ) {
        logger.debug("Kall mottatt på deleteDokument for $dokumentId")
        dokumentUnderArbeidService.slettDokument(
            behandlingId = behandlingId,
            dokumentId = DokumentId(dokumentId),
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
    }

    @PutMapping("/{dokumentId}/parent")
    fun kobleEllerFrikobleVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") persistentDokumentId: UUID,
        @RequestBody input: OptionalPersistentDokumentIdInput
    ): DokumentView {
        logger.debug("Kall mottatt på kobleEllerFrikobleVedlegg for $persistentDokumentId")
        try {


            val hovedDokument = if (input.dokumentId == null) {
                dokumentUnderArbeidService.frikobleVedlegg(
                    behandlingId = behandlingId,
                    dokumentId = DokumentId(persistentDokumentId),
                    innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
                )
            } else {
                dokumentUnderArbeidService.kobleVedlegg(
                    behandlingId = behandlingId,
                    dokumentId = DokumentId(input.dokumentId),
                    dokumentIdHovedDokumentSomSkalBliVedlegg = DokumentId(persistentDokumentId),
                    innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
                )
            }
            return dokumentMapper.mapToDokumentView(hovedDokument)
        } catch (e: Exception) {
            logger.error("Feilet under kobling av dokument $persistentDokumentId med ${input.dokumentId}", e)
            throw e
        }
    }

    @GetMapping
    fun findHovedDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentUnderArbeidService.findDokumenterNotFinished(behandlingId = behandlingId, ident = ident)
            .map { dokumentMapper.mapToDokumentView(it) }
    }

    @GetMapping("/smart")
    fun findSmartDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentUnderArbeidService.findSmartDokumenter(behandlingId = behandlingId, ident = ident)
            .map { dokumentMapper.mapToDokumentView(it) }
    }

    @PostMapping("/{dokumentid}/ferdigstill")
    fun idempotentOpprettOgFerdigstillDokumentEnhetFraHovedDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
        @RequestBody(required = false) input: FerdigstillDokumentInput?,
    ): DokumentView {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.finnOgMarkerFerdigHovedDokument(
                behandlingId = behandlingId,
                dokumentId = DokumentId(dokumentId),
                ident = ident,
                brevmottakertyper = input?.brevmottakertyper?.map { Brevmottakertype.of(it) }?.toSet()
                    ?: setOf(
                        Brevmottakertype.KLAGER,
                        Brevmottakertype.SAKEN_GJELDER,
                        Brevmottakertype.PROSESSFULLMEKTIG
                    ),
            )
        )
    }


    @GetMapping("/events")
    fun documentEvents(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestParam("lastEventIdInput", required = false) lastEventIdInput: UUID?,
        request: HttpServletRequest,
    ): SseEmitter {
        logger.debug("Kall mottatt på documentEvents for behandlingId $behandlingId")

        //Sjekker tilgang på behandlingsnivå
        behandlingService.getBehandling(behandlingId)

        val emitter = SseEmitter(Duration.ofHours(20).toMillis())

        val initial = SseEmitter.event()
            .reconnectTime(200)
        emitter.send(initial)

        //Try header first
        val lastEventIdHeaderName = "last-event-id"
        val lastEventId = if (request.getHeader(lastEventIdHeaderName) != null) {
            UUID.fromString(request.getHeader(lastEventIdHeaderName))
        } else {
            lastEventIdInput
        }

        var lastFinishedDocumentDateTime = if (lastEventId != null) {
            dokumentUnderArbeidRepository.findById(DokumentId(lastEventId)).get().ferdigstilt!!
        } else {
            LocalDateTime.now().minusMinutes(10)
        }

        timer(period = Duration.ofSeconds(eventDelay).toMillis()) {
            try {
                val documents = dokumentUnderArbeidService.findFinishedDokumenterAfterDateTime(
                    behandlingId = behandlingId,
                    fromDateTime = lastFinishedDocumentDateTime
                )

                if (documents.isNotEmpty()) {
                    lastFinishedDocumentDateTime = documents.maxOf { it.ferdigstilt!! }
                }

                documents.forEach {
                    val builder = SseEmitter.event()
                        .name("finished")
                        .data(it.id.id.toString())
                        .reconnectTime(200)
                        .id(it.id.id.toString())
                    emitter.send(builder)
                }

            } catch (e: Exception) {
                logger.error("Failed polling. Stopping timer.", e)
                emitter.completeWithError(e)
                this.cancel()
            }
        }
        return emitter
    }

    @PutMapping("/{dokumentid}/tittel")
    fun changeDocumentTitle(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
        @RequestBody input: DokumentTitleInput,
    ): DokumentView {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.updateDokumentTitle(
                behandlingId = behandlingId,
                dokumentId = DokumentId(dokumentId),
                dokumentTitle = input.title,
                innloggetIdent = ident,
            )
        )
    }
}