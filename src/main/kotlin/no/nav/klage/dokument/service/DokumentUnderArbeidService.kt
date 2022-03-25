package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.OpplastetMellomlagretDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentId
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentType
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.addSaksdokument
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import javax.transaction.Transactional

@Service
@Transactional
class DokumentUnderArbeidService(
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val attachmentValidator: MellomlagretDokumentValidatorService,
    private val mellomlagerService: MellomlagerService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val behandlingService: BehandlingService,
    private val dokumentEnhetService: KabalDocumentGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val safClient: SafGraphQlClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun opprettOgMellomlagreNyttHoveddokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: MellomlagretDokument?,
        json: String?,
        innloggetIdent: String,
        tittel: String,
    ): DokumentUnderArbeid {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId)

        if (opplastetFil != null) {
            attachmentValidator.validateAttachment(opplastetFil)
            val mellomlagerId = mellomlagerService.uploadDocument(opplastetFil)
            val hovedDokument = dokumentUnderArbeidRepository.save(
                DokumentUnderArbeid(
                    mellomlagerId = mellomlagerId,
                    opplastet = LocalDateTime.now(),
                    size = opplastetFil.content.size.toLong(),
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    smartEditorId = null,
                )
            )
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = null,
                tilVerdi = hovedDokument.opplastet.toString(),
                tidspunkt = hovedDokument.opplastet,
                dokumentId = hovedDokument.id,
            )
            return hovedDokument
        } else {
            if (json == null) {
                throw DokumentValidationException("Ingen json angitt")
            }
            val (smartEditorDokument, opplastet) =
                smartEditorApiGateway.createDocument(json, dokumentType, innloggetIdent, tittel)
            //TODO: smartEditorDokument har bogus tittel, ble ikke sendt med i kallet til smartEditorApi
            val mellomlagerId = mellomlagerService.uploadByteArray(tittel, smartEditorDokument.content)
            val hovedDokument = dokumentUnderArbeidRepository.save(
                DokumentUnderArbeid(
                    mellomlagerId = mellomlagerId,
                    opplastet = opplastet,
                    size = smartEditorDokument.content.size.toLong(),
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    smartEditorId = smartEditorDokument.smartEditorId,
                )
            )
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = null,
                tilVerdi = hovedDokument.opplastet.toString(),
                tidspunkt = hovedDokument.opplastet,
                dokumentId = hovedDokument.id,
            )
            return hovedDokument
        }
    }

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        dokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)

        if (dokumentUnderArbeid.parentId != null) {
            //Vi skal ikke kunne endre dokumentType på vedlegg
            throw DokumentValidationException("Man kan ikke endre dokumentType på vedlegg")
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }
        dokumentUnderArbeid.dokumentType = dokumentType
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = null,
            tilVerdi = dokumentUnderArbeid.opplastet.toString(),
            tidspunkt = dokumentUnderArbeid.opplastet,
            dokumentId = dokumentUnderArbeid.id,
        )
        return dokumentUnderArbeid
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokument = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokument.behandlingId)

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
        }

        val oldValue = dokument.name
        dokument.name = dokumentTitle
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
            fraVerdi = oldValue,
            tilVerdi = dokument.name,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )
        return dokument
    }

    private fun updateJournalpostId(
        behandlingId: UUID,
        dokumentId: DokumentId,
        journalpostId: String
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getById(dokumentId)

        val behandling = behandlingService.getBehandlingForUpdateBySystembruker(behandlingId)

        val oldValue = dokument.journalpostId
        dokument.journalpostId = journalpostId

        behandling.publishEndringsloggEvent(
            saksbehandlerident = SYSTEMBRUKER,
            felt = Felt.DOKUMENT_UNDER_ARBEID_JOURNALPOST_ID,
            fraVerdi = oldValue,
            tilVerdi = dokument.journalpostId,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )

        return dokument
    }

    fun finnOgMarkerFerdigHovedDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        ident: String
    ): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

        if (hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        if (hovedDokument.parentId != null) {
            throw DokumentValidationException("Kan ikke markere et vedlegg som ferdig")
        }

        val now = LocalDateTime.now()
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(now)
        vedlegg.forEach { it.markerFerdigHvisIkkeAlleredeMarkertFerdig(now) }

        //Etter at et dokument er markert som ferdig skal det ikke kunne endres. Vi henter derfor en snapshot av tilstanden slik den er nå
        if (hovedDokument.smartEditorId != null) {
            mellomlagreNyVersjonAvSmartEditorDokument(hovedDokument)
        }
        vedlegg.forEach {
            if (it.smartEditorId != null) {
                mellomlagreNyVersjonAvSmartEditorDokument(it)
            }
        }

        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_MARKERT_FERDIG,
            fraVerdi = null,
            tilVerdi = hovedDokument.markertFerdig.toString(),
            tidspunkt = LocalDateTime.now(),
            dokumentId = hovedDokument.id,
        )
        return hovedDokument
    }


    fun hentMellomlagretDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        innloggetIdent: String
    ): MellomlagretDokument {
        val dokument =
            dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(dokument.behandlingId)

        if (dokument.isStaleSmartEditorDokument()) {
            mellomlagreNyVersjonAvSmartEditorDokument(dokument)
        }

        return OpplastetMellomlagretDokument(
            title = dokument.name,
            content = mellomlagerService.getUploadedDocument(dokument.mellomlagerId),
            contentType = MediaType.APPLICATION_PDF
        )
    }

    fun slettDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        innloggetIdent: String
    ) {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke slette et dokument som er ferdigstilt")
        }

        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(dokumentUnderArbeid.id)
        if (vedlegg.isNotEmpty()) {
            throw DokumentValidationException("Kan ikke slette dokument med vedlegg")
        }
        if (dokumentUnderArbeid.smartEditorId != null) {
            smartEditorApiGateway.deleteDocument(dokumentUnderArbeid.smartEditorId!!)
        }
        dokumentUnderArbeidRepository.delete(dokumentUnderArbeid)

        mellomlagerService.deleteDocument(dokumentUnderArbeid.mellomlagerId)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
            fraVerdi = dokumentUnderArbeid.opplastet.toString(),
            tilVerdi = null,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokumentUnderArbeid.id,
        )
    }

    fun kobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        dokumentIdHovedDokumentSomSkalBliVedlegg: DokumentId,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (hovedDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        val hovedDokumentSomSkalBliVedlegg =
            dokumentUnderArbeidRepository.getById(dokumentIdHovedDokumentSomSkalBliVedlegg)

        if (hovedDokumentSomSkalBliVedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        val vedlegg =
            dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokumentSomSkalBliVedlegg.id)
        if (vedlegg.isNotEmpty()) {
            throw DokumentValidationException("Et dokument som selv har vedlegg kan ikke bli et vedlegg")
        }
        hovedDokumentSomSkalBliVedlegg.parentId = hovedDokument.id
        return hovedDokumentSomSkalBliVedlegg
    }

    fun frikobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: DokumentId,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val vedlegg = dokumentUnderArbeidRepository.getById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(vedlegg.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (vedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        vedlegg.parentId = null
        return vedlegg
    }

    fun findDokumenterNotFinished(behandlingId: UUID, ident: String): SortedSet<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNullOrderByCreated(behandlingId)
    }

    fun findFinishedDokumenterAfterDateTime(
        behandlingId: UUID,
        fromDateTime: LocalDateTime
    ): SortedSet<DokumentUnderArbeid> {
        val data =
            dokumentUnderArbeidRepository.findByMarkertFerdigNotNullAndFerdigstiltNotNullAndParentIdIsNullAndBehandlingIdAndFerdigstiltAfter(
                behandlingId,
                fromDateTime
            )
        return data
    }

    fun findSmartDokumenter(behandlingId: UUID, ident: String): SortedSet<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return dokumentUnderArbeidRepository.findByBehandlingIdAndSmartEditorIdNotNullOrderByCreated(behandlingId)
    }

    fun opprettDokumentEnhet(hovedDokumentId: DokumentId) {
        val hovedDokument = dokumentUnderArbeidRepository.getById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        if (hovedDokument.dokumentEnhetId == null) {
            //Vi vet at smartEditor-dokumentene har en oppdatert snapshot i mellomlageret fordi det ble fikset i finnOgMarkerFerdigHovedDokument
            val behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
            val dokumentEnhetId = dokumentEnhetService.createKomplettDokumentEnhet(behandling, hovedDokument, vedlegg)
            hovedDokument.dokumentEnhetId = dokumentEnhetId
        }
    }

    fun ferdigstillDokumentEnhet(hovedDokumentId: DokumentId) {
        val hovedDokument = dokumentUnderArbeidRepository.getById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        val behandling: Behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
        val journalpostId =
            dokumentEnhetService.fullfoerDokumentEnhet(dokumentEnhetId = hovedDokument.dokumentEnhetId!!)

        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId.value)

        updateJournalpostId(behandling.id, hovedDokumentId, journalpostId.value)

        val saksdokumenter = journalpost.mapToSaksdokumenter()
        saksdokumenter.forEach { saksdokument ->
            val saksbehandlerIdent =
                behandling.tildelingHistorikk.maxByOrNull { it.tildeling.tidspunkt }?.tildeling?.saksbehandlerident
                    ?: "SYSTEMBRUKER" //TODO: Er dette innafor? Burde vi evt lagre ident i HovedDokument, så vi kan hente det derfra?
            behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                ?.also { applicationEventPublisher.publishEvent(it) }
        }
        val now = LocalDateTime.now()
        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
        vedlegg.forEach { it.ferdigstillHvisIkkeAlleredeFerdigstilt(now) }
    }

    fun getSmartEditorId(dokumentId: DokumentId, readOnly: Boolean): UUID {
        val dokumentUnderArbeid =
            dokumentUnderArbeidRepository.getById(dokumentId)
                ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        if (readOnly) {
            behandlingService.getBehandling(dokumentUnderArbeid.behandlingId)
        } else {
            behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)
        }

        return dokumentUnderArbeid.smartEditorId
            ?: throw DokumentValidationException("${dokumentId.id} er ikke et smarteditor dokument")
    }

    private fun mellomlagreNyVersjonAvSmartEditorDokument(dokument: DokumentUnderArbeid) {
        val mellomlagerId =
            mellomlagerService.uploadDocument(
                smartEditorApiGateway.getDocumentAsPDF(
                    dokument.smartEditorId!!,
                    dokument.name
                )
            )
        //Sletter gammelt:
        mellomlagerService.deleteDocument(dokument.mellomlagerId)
        dokument.mellomlagerId = mellomlagerId
        dokument.opplastet = LocalDateTime.now()
    }

    private fun DokumentUnderArbeid.isStaleSmartEditorDokument() =
        this.smartEditorId != null && !this.erMarkertFerdig() && smartEditorApiGateway.isMellomlagretDokumentStale(
            this.smartEditorId!!,
            this.opplastet
        )

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident,
            felt,
            fraVerdi,
            tilVerdi,
            this.id,
            tidspunkt
        )
    }

    private fun Behandling.publishEndringsloggEvent(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime,
        dokumentId: DokumentId,
    ) {
        listOfNotNull(
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.DOKUMENT_UNDER_ARBEID_ID,
                fraVerdi = fraVerdi.let { dokumentId.id.toString() },
                tilVerdi = tilVerdi.let { dokumentId.id.toString() },
                tidspunkt = tidspunkt,
            ),
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = felt,
                fraVerdi = fraVerdi,
                tilVerdi = tilVerdi,
                tidspunkt = tidspunkt,
            )
        ).let {
            applicationEventPublisher.publishEvent(
                BehandlingEndretEvent(
                    behandling = this,
                    endringslogginnslag = it
                )
            )
        }
    }

    private fun Journalpost?.mapToSaksdokumenter(): List<Saksdokument> {
        return this?.dokumenter?.map {
            Saksdokument(
                journalpostId = this.journalpostId,
                dokumentInfoId = it.dokumentInfoId
            )
        } ?: emptyList()
    }
}


