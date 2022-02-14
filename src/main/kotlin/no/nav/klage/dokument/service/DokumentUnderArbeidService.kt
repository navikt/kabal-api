package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.MellomlagretDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.repositories.HovedDokumentRepository
import no.nav.klage.dokument.repositories.findDokumentUnderArbeidByPersistentDokumentIdOrVedleggPersistentDokumentId
import no.nav.klage.dokument.repositories.getDokumentUnderArbeidByPersistentDokumentIdOrVedleggPersistentDokumentId
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
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import javax.transaction.Transactional

@Service
@Transactional
class DokumentUnderArbeidService(
    private val hovedDokumentRepository: HovedDokumentRepository,
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
    }

    fun opprettOgMellomlagreNyttHoveddokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: MellomlagretDokument?,
        json: String?,
        innloggetIdent: String,
        tittel: String?,
    ): DokumentMedParentReferanse {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId)

        if (opplastetFil != null) {
            attachmentValidator.validateAttachment(opplastetFil)
            val mellomlagerId = mellomlagerService.uploadDocument(opplastetFil)
            val hovedDokument = hovedDokumentRepository.save(
                HovedDokument(
                    mellomlagerId = mellomlagerId,
                    opplastet = LocalDateTime.now(),
                    size = opplastetFil.content.size.toLong(),
                    name = tittel ?: opplastetFil.title,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                )
            )
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = null,
                tilVerdi = hovedDokument.opplastet.toString(),
                tidspunkt = hovedDokument.opplastet,
                persistentDokumentId = hovedDokument.persistentDokumentId,
            )
            return hovedDokument.toDokumentMedParentReferanse()
        } else {
            if (json == null) {
                throw DokumentValidationException("Ingen json angitt")
            }
            val (smartEditorDokument, opplastet) =
                smartEditorApiGateway.createDocument(json, dokumentType, innloggetIdent)
            val mellomlagerId = mellomlagerService.uploadDocument(smartEditorDokument)
            val hovedDokument = hovedDokumentRepository.save(
                HovedDokument(
                    mellomlagerId = mellomlagerId,
                    opplastet = opplastet,
                    size = smartEditorDokument.content.size.toLong(),
                    name = tittel ?: "filnavn.pdf",
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
                persistentDokumentId = hovedDokument.persistentDokumentId,
            )
            return hovedDokument.toDokumentMedParentReferanse()
        }
    }

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        dokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentMedParentReferanse {

        //Skal ikke kunne endre dokumentType på vedlegg, så jeg spør her bare etter hoveddokumenter
        val hovedDokument = hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentId)
            ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

        if (hovedDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }
        hovedDokument.dokumentType = dokumentType
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = null,
            tilVerdi = hovedDokument.opplastet.toString(),
            tidspunkt = hovedDokument.opplastet,
            persistentDokumentId = hovedDokument.persistentDokumentId,
        )
        return hovedDokument.toDokumentMedParentReferanse()
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentMedParentReferanse {

        val hovedDokument: HovedDokument? = hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentId)
        if (hovedDokument != null) {

            //Sjekker tilgang på behandlingsnivå:
            val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

            if (hovedDokument.erMarkertFerdig()) {
                throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
            }

            val oldValue = hovedDokument.name
            hovedDokument.name = dokumentTitle
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
                fraVerdi = oldValue,
                tilVerdi = hovedDokument.name,
                tidspunkt = LocalDateTime.now(),
                persistentDokumentId = hovedDokument.persistentDokumentId,
            )
            return hovedDokument.toDokumentMedParentReferanse()
        } else {
            val hovedDokumentMedVedlegg =
                hovedDokumentRepository.findByVedleggPersistentDokumentId(persistentDokumentId)
                    ?: throw DokumentValidationException("Dokument ikke funnet")

            //Sjekker tilgang på behandlingsnivå:
            val behandling = behandlingService.getBehandlingForUpdate(hovedDokumentMedVedlegg.behandlingId)

            if (hovedDokumentMedVedlegg.erMarkertFerdig()) {
                throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
            }

            var vedlegg = hovedDokumentMedVedlegg.findDokumentUnderArbeidByPersistentDokumentId(persistentDokumentId)
                ?: throw DokumentValidationException("Dokument ikke funnet")

            vedlegg = vedlegg as Vedlegg

            val oldValue = vedlegg.name
            vedlegg.name = dokumentTitle
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
                fraVerdi = oldValue,
                tilVerdi = vedlegg.name,
                tidspunkt = LocalDateTime.now(),
                persistentDokumentId = vedlegg.persistentDokumentId,
            )
            return vedlegg.toDokumentMedParentReferanse(hovedDokumentMedVedlegg.persistentDokumentId)
        }
    }

    fun finnOgMarkerFerdigHovedDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        hovedDokumentPersistentDokumentId: PersistentDokumentId,
        ident: String
    ): DokumentMedParentReferanse {
        val hovedDokument = hovedDokumentRepository.findByPersistentDokumentId(hovedDokumentPersistentDokumentId)
            ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

        if (hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        if (hovedDokument.dokumentType == DokumentType.VEDLEGG) {
            throw DokumentValidationException("Kan ikke markere et vedlegg som ferdig")
        }

        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig()
        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_MARKERT_FERDIG,
            fraVerdi = null,
            tilVerdi = hovedDokument.markertFerdig.toString(),
            tidspunkt = LocalDateTime.now(),
            persistentDokumentId = hovedDokument.persistentDokumentId,
        )
        return hovedDokument.toDokumentMedParentReferanse()
    }


    fun hentMellomlagretDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        innloggetIdent: String
    ): MellomlagretDokument {
        val dokument: DokumentUnderArbeid =
            hovedDokumentRepository.findDokumentUnderArbeidByPersistentDokumentIdOrVedleggPersistentDokumentId(
                persistentDokumentId
            ) ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(dokument.behandlingId)

        if (dokument.isStaleSmartEditorDokument()) {
            mellomlagreSmartEditorDokument(dokument.smartEditorId!!)
        }
        return mellomlagerService.getUploadedDocument(dokument.mellomlagerId)
    }

    fun slettDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        innloggetIdent: String
    ) {
        val hovedDokument: HovedDokument? = hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentId)
        if (hovedDokument != null) {

            //Sjekker tilgang på behandlingsnivå:
            val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

            if (hovedDokument.erMarkertFerdig()) {
                throw DokumentValidationException("Kan ikke slette et dokument som er ferdigstilt")
            }
            if (hovedDokument.harVedlegg()) {
                throw DokumentValidationException("Kan ikke slette DokumentEnhet med vedlegg")
            }
            if (hovedDokument.smartEditorId != null) {
                smartEditorApiGateway.deleteDocument(hovedDokument.smartEditorId!!)
            }
            hovedDokumentRepository.delete(hovedDokument)
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = hovedDokument.opplastet.toString(),
                tilVerdi = null,
                tidspunkt = LocalDateTime.now(),
                persistentDokumentId = hovedDokument.persistentDokumentId,
            )

        } else {
            val hovedDokumentMedVedlegg =
                hovedDokumentRepository.findByVedleggPersistentDokumentId(persistentDokumentId)
                    ?: throw DokumentValidationException("Dokument ikke funnet")

            //Sjekker tilgang på behandlingsnivå:
            val behandling = behandlingService.getBehandlingForUpdate(hovedDokumentMedVedlegg.behandlingId)

            if (hovedDokumentMedVedlegg.erMarkertFerdig()) {
                throw DokumentValidationException("Kan ikke slette et dokument som er ferdigstilt")
            }
            if (hovedDokumentMedVedlegg.harVedlegg()) {
                throw DokumentValidationException("Kan ikke slette DokumentEnhet med vedlegg")

            }
            val vedlegg = hovedDokumentMedVedlegg.findDokumentUnderArbeidByPersistentDokumentId(persistentDokumentId)
                ?: throw DokumentValidationException("Dokument ikke funnet")
            if (vedlegg.smartEditorId != null) {
                smartEditorApiGateway.deleteDocument(vedlegg.smartEditorId!!)
            }
            hovedDokumentMedVedlegg.vedlegg.remove(vedlegg)
            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = vedlegg.opplastet.toString(),
                tilVerdi = null,
                tidspunkt = LocalDateTime.now(),
                persistentDokumentId = vedlegg.persistentDokumentId,
            )
        }

    }

    fun kobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        persistentDokumentIdHovedDokumentSomSkalBliVedlegg: PersistentDokumentId,
        innloggetIdent: String
    ): DokumentMedParentReferanse {
        val hovedDokument = hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentId)
            ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (hovedDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        val hovedDokumentSomSkalBliVedlegg =
            hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentIdHovedDokumentSomSkalBliVedlegg)
                ?: throw DokumentValidationException("Dokument ikke funnet")
        if (hovedDokumentSomSkalBliVedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }
        if (hovedDokumentSomSkalBliVedlegg.dokumentType != DokumentType.VEDLEGG) {
            throw DokumentValidationException("Kan ikke koble et dokument som ikke er et vedlegg")
        }
        if (hovedDokumentSomSkalBliVedlegg.harVedlegg()) {
            throw DokumentValidationException("Et dokument som selv har vedlegg kan ikke bli et vedlegg")
        }

        hovedDokumentRepository.delete(hovedDokumentSomSkalBliVedlegg)
        val nyttVedlegg = hovedDokumentSomSkalBliVedlegg.toVedlegg()
        hovedDokument.vedlegg.add(nyttVedlegg)
        return nyttVedlegg.toDokumentMedParentReferanse(hovedDokument.persistentDokumentId)
    }

    fun frikobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentId: PersistentDokumentId,
        persistentDokumentIdVedlegg: PersistentDokumentId,
        innloggetIdent: String
    ): DokumentMedParentReferanse {
        val hovedDokument = hovedDokumentRepository.findByPersistentDokumentId(persistentDokumentId)
            ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (hovedDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        val vedlegg =
            hovedDokument.findVedleggByPersistentDokumentId(persistentDokumentIdVedlegg)
                ?: throw DokumentValidationException("Dokument ikke funnet")

        hovedDokument.vedlegg.remove(vedlegg)
        return hovedDokumentRepository.save(vedlegg.toHovedDokument()).toDokumentMedParentReferanse()
    }

    fun frikobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        persistentDokumentIdVedlegg: PersistentDokumentId,
        innloggetIdent: String
    ): DokumentMedParentReferanse {
        val hovedDokument = hovedDokumentRepository.findByVedleggPersistentDokumentId(persistentDokumentIdVedlegg)
            ?: throw DokumentValidationException("Dokument ikke funnet")

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (hovedDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        val vedlegg =
            hovedDokument.findVedleggByPersistentDokumentId(persistentDokumentIdVedlegg)
                ?: throw DokumentValidationException("Dokument ikke funnet")

        hovedDokument.vedlegg.remove(vedlegg)
        return hovedDokumentRepository.save(vedlegg.toHovedDokument()).toDokumentMedParentReferanse()
    }

    fun findHovedDokumenter(behandlingId: UUID, ident: String): SortedSet<DokumentMedParentReferanse> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return hovedDokumentRepository.findByBehandlingIdOrderByCreated(behandlingId)
            .flatMap { it.toDokumenterMedParentReferanse() }
            .toSortedSet()
    }

    fun findSmartDokumenter(behandlingId: UUID, ident: String): SortedSet<DokumentMedParentReferanse> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return hovedDokumentRepository.findByBehandlingIdOrderByCreated(behandlingId)
            .flatMap { it.toDokumenterMedParentReferanse() }
            .filter { it.smartEditorId != null }
            .toSortedSet()
    }

    fun opprettDokumentEnhet(hovedDokumentId: DokumentId) {

        val hovedDokument = hovedDokumentRepository.getById(hovedDokumentId)
        if (hovedDokument.dokumentEnhetId == null) {
            //TODO: Løp gjennom og refresh alle smarteditor-dokumenter
            val behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
            val dokumentEnhetId = dokumentEnhetService.createKomplettDokumentEnhet(behandling, hovedDokument)
            hovedDokument.dokumentEnhetId = dokumentEnhetId
        }
    }

    fun ferdigstillDokumentEnhet(hovedDokumentId: DokumentId) {
        val hovedDokument = hovedDokumentRepository.getById(hovedDokumentId)
        val behandling: Behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
        val journalpostId =
            dokumentEnhetService.fullfoerDokumentEnhet(dokumentEnhetId = hovedDokument.dokumentEnhetId!!)

        val journalpost = safClient.getJournalpostAsSystembruker(journalpostId.value)
        val saksdokumenter = journalpost.mapToSaksdokumenter()
        saksdokumenter.forEach { saksdokument ->
            val saksbehandlerIdent =
                behandling.tildelingHistorikk.maxByOrNull { it.tildeling.tidspunkt }?.tildeling?.saksbehandlerident
                    ?: "SYSTEMBRUKER" //TODO: Er dette innafor? Burde vi evt lagre ident i HovedDokument, så vi kan hente det derfra?
            behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                ?.also { applicationEventPublisher.publishEvent(it) }
        }
        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt()
    }

    fun getSmartEditorId(persistentDokumentId: PersistentDokumentId, readOnly: Boolean): UUID {
        val dokumentUnderArbeid =
            hovedDokumentRepository.getDokumentUnderArbeidByPersistentDokumentIdOrVedleggPersistentDokumentId(
                persistentDokumentId
            )

        //Sjekker tilgang på behandlingsnivå:
        if (readOnly) {
            behandlingService.getBehandling(dokumentUnderArbeid.behandlingId)
        } else {
            behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)
        }

        return dokumentUnderArbeid.smartEditorId
            ?: throw DokumentValidationException("${persistentDokumentId.persistentDokumentId} er ikke et smarteditor dokument")
    }

    private fun mellomlagreSmartEditorDokument(smartEditorId: UUID) {
        mellomlagerService.uploadDocument(smartEditorApiGateway.getDocumentAsPDF(smartEditorId))
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
        persistentDokumentId: PersistentDokumentId,
    ) {
        listOfNotNull(
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.DOKUMENT_UNDER_ARBEID_ID,
                fraVerdi = fraVerdi.let { persistentDokumentId.persistentDokumentId.toString() },
                tilVerdi = tilVerdi.let { persistentDokumentId.persistentDokumentId.toString() },
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


