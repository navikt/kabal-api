package no.nav.klage.dokument.service

import com.fasterxml.jackson.databind.ObjectMapper
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.oppgave.clients.saf.SafFacade
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
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.hibernate.Hibernate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
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
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    @Scheduled(fixedDelayString = "\${FERDIGSTILLE_DOKUMENTER_DELAY_MILLIS}", initialDelay = 45000)
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun ferdigstillHovedDokumenter() {
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
        val dua =
            Hibernate.unproxy(dokumentFerdigstiltAvSaksbehandler.dokumentUnderArbeid) as DokumentUnderArbeidAsHoveddokument
        ferdigstill(dua)
    }

    private fun ferdigstill(it: DokumentUnderArbeidAsHoveddokument) {
        logger.debug("ferdigstill hoveddokument with id {}", it.id)
        var updatedDokument = it
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
                data = objectMapper.writeValueAsString(
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

        } catch (e: Exception) {
            logger.error("Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}. See team-logs for more details.")
            teamLogger.error(
                "Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}",
                e
            )
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