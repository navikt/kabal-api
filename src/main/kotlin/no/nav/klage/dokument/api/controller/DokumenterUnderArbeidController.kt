package no.nav.klage.dokument.api.controller


import io.swagger.annotations.Api
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentType
import no.nav.klage.dokument.domain.dokumenterunderarbeid.PersistentDokumentId
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api-dokumenter"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/dokumenter")
class DokumentUnderArbeidController(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerRepository,
    private val dokumentMapper: DokumentMapper,
    private val dokumenInputMapper: DokumentInputMapper,
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
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                behandlingId = behandlingId,
                dokumentType = DokumentType.VEDTAK,
                opplastetFil = dokumenInputMapper.mapToMellomlagretDokument(input.file),
                json = null,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = input.tittel
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
                dokumentType = DokumentType.VEDTAK,
                opplastetFil = null,
                json = body.json,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = body.tittel
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
                persistentDokumentId = PersistentDokumentId(dokumentId),
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
                persistentDokumentId = PersistentDokumentId(dokumentId),
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
            persistentDokumentId = PersistentDokumentId(dokumentId),
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
        val hovedDokument = if (input.dokumentId == null) {
            dokumentUnderArbeidService.frikobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentIdVedlegg = PersistentDokumentId(persistentDokumentId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        } else {
            dokumentUnderArbeidService.kobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentId = PersistentDokumentId(input.dokumentId),
                persistentDokumentIdHovedDokumentSomSkalBliVedlegg = PersistentDokumentId(persistentDokumentId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        }
        return dokumentMapper.mapToDokumentView(hovedDokument)
    }

    @PostMapping("/{dokumentId}/vedlegg")
    fun kobleVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") persistentDokumentId: UUID,
        @RequestBody input: PersistentDokumentIdInput
    ): DokumentView {
        logger.debug("Kall mottatt på kobleVedlegg for $persistentDokumentId")
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.kobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentId = PersistentDokumentId(persistentDokumentId),
                persistentDokumentIdHovedDokumentSomSkalBliVedlegg = PersistentDokumentId(input.dokumentId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{dokumentId}/vedlegg/{dokumentIdVedlegg}")
    fun fristillVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") persistentDokumentId: UUID,
        @PathVariable("dokumentIdVedlegg") persistentDokumentIdVedlegg: UUID,
    ): DokumentView {
        logger.debug("Kall mottatt på fristillVedlegg for $persistentDokumentId og $persistentDokumentIdVedlegg")
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.frikobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentId = PersistentDokumentId(persistentDokumentId),
                persistentDokumentIdVedlegg = PersistentDokumentId(persistentDokumentIdVedlegg),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @GetMapping
    fun findHovedDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentUnderArbeidService.findHovedDokumenter(behandlingId = behandlingId, ident = ident)
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
        @PathVariable("dokumentid") dokumentId: UUID
    ): DokumentView {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.finnOgMarkerFerdigHovedDokument(
                behandlingId = behandlingId,
                hovedDokumentPersistentDokumentId = PersistentDokumentId(dokumentId),
                ident = ident
            )
        )
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
                persistentDokumentId = PersistentDokumentId(dokumentId),
                dokumentTitle = input.title,
                innloggetIdent = ident,
            )
        )
    }
}