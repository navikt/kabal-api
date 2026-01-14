package no.nav.klage.dokument.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.config.SchedulerHealthGate
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.kafka.DocumentFinishedEvent
import no.nav.klage.oppgave.domain.kafka.Employee
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KafkaInternalEventService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.hibernate.Hibernate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Service
class FerdigstillDokumentService(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val saksbehandlerService: SaksbehandlerService,
    private val safFacade: SafFacade,
    private val dokumentMapper: DokumentMapper,
    private val behandlingService: BehandlingService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val schedulerHealthGate: SchedulerHealthGate,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    @Scheduled(cron = "*/30 * * * * *")
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun ferdigstillHovedDokumenter() {
        if (!schedulerHealthGate.isReady()) return
        val hovedDokumenterIkkeFerdigstilte =
            dokumentUnderArbeidCommonService.findHoveddokumenterByMarkertFerdigNotNullAndFerdigstiltNull()
        for (it in hovedDokumenterIkkeFerdigstilte) {
            ferdigstill(it)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun listenToFerdigstilteDokumenterAvSaksbehandler(dokumentFerdigstiltAvSaksbehandler: DokumentFerdigstiltAvSaksbehandler) {
        logger.debug("listenToFerdigstilteDokumenterAvSaksbehandler called")
        val hoveddokument =
            Hibernate.unproxy(dokumentFerdigstiltAvSaksbehandler.dokumentUnderArbeid) as DokumentUnderArbeidAsHoveddokument
        ferdigstill(hoveddokument = hoveddokument)
    }

    private fun ferdigstill(hoveddokument: DokumentUnderArbeidAsHoveddokument) {
        logger.debug("ferdigstill hoveddokument with id {}", hoveddokument.id)
        var updatedDokument = hoveddokument
        try {
            if (updatedDokument.dokumentEnhetId == null) {
                updatedDokument = dokumentUnderArbeidService.opprettDokumentEnhet(updatedDokument.id)
            }
            updatedDokument = dokumentUnderArbeidService.ferdigstillDokumentEnhet(updatedDokument.id)

            logger.debug(
                "dokumentUnderArbeidService.ferdigstillDokumentEnhet(updatedDokument.id) for document with id {} done",
                updatedDokument.id
            )

            val behandling =
                behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(behandlingId = updatedDokument.behandlingId)

            val dokumentReferanseList = updatedDokument.dokarkivReferences.map {
                val journalpost = safFacade.getJournalpostAsSystembruker(journalpostId = it.journalpostId)

                dokumentMapper.mapJournalpostToDokumentReferanse(
                    journalpost = journalpost,
                    saksdokumenter = behandling.saksdokumenter
                )
            }

            publishInternalEvent(
                data = jacksonObjectMapper.writeValueAsString(
                    DocumentFinishedEvent(
                        actor = Employee(
                            navIdent = updatedDokument.markertFerdigBy!!,
                            navn = saksbehandlerService.getNameForIdentDefaultIfNull(updatedDokument.markertFerdigBy!!),
                        ),
                        timestamp = updatedDokument.ferdigstilt!!,
                        id = updatedDokument.id.toString(),
                        journalpostList = dokumentReferanseList.map {
                            //small hack for now, until we fetch data from SAF on consumer side of event.
                            it.copy(
                                harTilgangTilArkivvariant = true,
                                hasAccess = true,
                                vedlegg = it.vedlegg.map { vedlegg ->
                                    vedlegg.copy(
                                        harTilgangTilArkivvariant = true,
                                        hasAccess = true,
                                    )
                                }.toMutableList()
                            )
                        },
                    )
                ),
                behandlingId = updatedDokument.behandlingId,
                type = InternalEventType.DOCUMENT_FINISHED,
            )
            logger.debug("ferdigstill for document with id {} successful", updatedDokument.id)

            deleteSmartdokumentAndPossibleVedlegg(hoveddokument = updatedDokument)

        } catch (e: Exception) {
            logger.error("Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}. See team-logs for more details.")
            teamLogger.error(
                "Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}",
                e
            )
        }
    }

    /**
     * Deletes smart documents and any smart document attachments associated with the given hoveddokument.
     *
     * This cleanup is performed after a document has been successfully finalized, to free up resources.
     * If deletion fails (e.g., due to network or API errors), the error is logged but does not affect the finalization process.
     * The finalization is considered complete regardless of the outcome of this cleanup.
     */
    private fun deleteSmartdokumentAndPossibleVedlegg(hoveddokument: DokumentUnderArbeidAsHoveddokument) {

        fun attemptToDeleteSmartdokument(smartEditorId: UUID) {
            try {
                logger.debug("Deleting smartdocument with smartEditorId {}", smartEditorId)
                smartEditorApiGateway.deleteDocument(smartEditorId)
            } catch (e: Exception) {
                logger.error("Could not delete smartdocument with smartEditorId $smartEditorId. See team-logs for more details.")
                teamLogger.error(
                    "Could not delete smartdocument with smartEditorId $smartEditorId",
                    e
                )
            }
        }

        val vedleggAsSmartdokument = dokumentUnderArbeidCommonService.findVedleggByParentId(hoveddokument.id)
            .filterIsInstance<SmartdokumentUnderArbeidAsVedlegg>()

        vedleggAsSmartdokument.forEach { vedlegg ->
            attemptToDeleteSmartdokument(vedlegg.smartEditorId)
        }

        if (hoveddokument is SmartdokumentUnderArbeidAsHoveddokument) {
            attemptToDeleteSmartdokument(hoveddokument.smartEditorId)
        }
    }

    private fun publishInternalEvent(data: String, behandlingId: UUID, type: InternalEventType) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = behandlingId.toString(),
                type = type,
                data = data,
            )
        )
    }
}